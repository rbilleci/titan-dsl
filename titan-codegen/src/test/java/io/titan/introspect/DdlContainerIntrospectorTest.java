package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit-level checks that need no Docker daemon. */
class DdlContainerIntrospectorTest {

    @Test
    void dockerUnavailableMessageIsActionable() {
        String message = DdlContainerIntrospector.dockerUnavailableMessage();
        assertTrue(message.contains("Docker"), message);
        assertTrue(message.contains("ddlMode = 'parser'"),
                "must name the no-Docker fallback configuration, was:\n" + message);
        assertTrue(message.contains("fails"),
                "must set expectations about the fallback's strictness, was:\n" + message);
    }

    @Test
    void dockerProbeNeverThrows() {
        // Whatever the environment, the probe must answer true/false instead of exploding.
        DdlContainerIntrospector.dockerAvailable();
    }
}
