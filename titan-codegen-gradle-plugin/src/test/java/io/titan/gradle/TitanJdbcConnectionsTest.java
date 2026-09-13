package io.titan.gradle;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitanJdbcConnectionsTest {

    @Test
    void missingDriverFailureIsActionableAndNamesTitanJdbcConfiguration() {
        GradleException failure = assertThrows(GradleException.class,
                () -> TitanJdbcConnections.open(Set.of(), "jdbc:nosuchdb://db.example.com:5432/app", "titan", "secret"));

        String message = failure.getMessage();
        assertTrue(message.contains("No JDBC driver accepts URL 'jdbc:nosuchdb://db.example.com:5432/app'"),
                "must explain which URL has no driver, was: " + message);
        assertTrue(message.contains("titanJdbc"), "must name the provisioning configuration, was: " + message);
        assertTrue(message.contains("dependencies {"), "must show how to add the driver, was: " + message);
    }

    @Test
    void missingDriverMessageSuggestsKnownCoordinatesByUrlPrefix() {
        assertTrue(TitanJdbcConnections.missingDriverMessage("jdbc:postgresql://h/db", List.of())
                .contains("org.postgresql:postgresql:42.7.13"));
        assertTrue(TitanJdbcConnections.missingDriverMessage("jdbc:mysql://h/db", List.of())
                .contains("com.mysql:mysql-connector-j:26.7.0"));
        assertTrue(TitanJdbcConnections.missingDriverMessage("jdbc:mariadb://h/db", List.of())
                .contains("org.mariadb.jdbc:mariadb-java-client"));
        assertTrue(TitanJdbcConnections.missingDriverMessage("jdbc:somethingelse://h/db", List.of())
                .contains("<groupId>:<artifactId>:<version>"));
    }

    // GAP G-4: connection failures must never leak credentials embedded in the URL.
    @Test
    void missingDriverMessageRedactsCredentialsInUrl() {
        String message = TitanJdbcConnections.missingDriverMessage(
                "jdbc:nosuchdb://titan:secret@db.example.com:5432/app?password=alsosecret", List.of());

        assertFalse(message.contains("secret"), "credentials must be redacted, was: " + message);
        assertTrue(message.contains("jdbc:nosuchdb://db.example.com:5432/app"));
    }

    @Test
    void redactsJdbcUrls() {
        assertEquals("jdbc:postgresql://db.example.com:5432/app",
                TitanJdbcConnections.redactedJdbcUrl("jdbc:postgresql://db.example.com:5432/app?user=titan&password=secret"));
        assertEquals("jdbc:mysql://db.example.com:3306/app",
                TitanJdbcConnections.redactedJdbcUrl("jdbc:mysql://titan:secret@db.example.com:3306/app"));
    }
}
