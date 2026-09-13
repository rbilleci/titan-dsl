package io.titan.gradle;

import org.gradle.api.GradleException;

import java.util.Locale;

/**
 * How DDL-file introspection turns {@code .sql} sources into a schema model (audit G-6/G-7).
 */
enum TitanDdlMode {

    /**
     * Default. Applies the DDL files to a scratch Testcontainers database for the configured
     * dialect and introspects it through the JDBC path — equivalent to live-database
     * introspection by construction. Requires Docker and a JDBC driver on {@code titanJdbc}.
     */
    CONTAINER,

    /**
     * No-Docker fallback: the regex DDL parser. Supports a reduced DDL subset and fails hard on
     * any statement it cannot represent instead of dropping it silently.
     */
    PARSER;

    static final String DEFAULT = "container";

    static TitanDdlMode parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                    "Unknown titan.database.ddlMode '" + value + "'. Valid values:\n"
                            + "  - 'container' (default): apply DDL to a scratch Testcontainers database and "
                            + "introspect via JDBC (requires Docker and a driver on the 'titanJdbc' configuration)\n"
                            + "  - 'parser': no-Docker fallback regex parser (reduced DDL subset; fails on "
                            + "unrecognized statements instead of dropping them)");
        }
    }
}
