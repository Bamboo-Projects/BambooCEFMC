package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without Chromium the runtime says so — and takes nothing down with it.
 *
 * <p><b>This test run stands exactly in the position of the player without a
 * runtime environment.</b> On the test bench {@code org.cef} is not on the
 * classpath: the built {@code jcef.jar} is {@code compileOnly}, and that
 * is enough to compile but not to run. What happens here therefore happens
 * for anyone whose game is missing the runtime environment.
 *
 * <p>The guarantee at stake is the most important of the whole runtime:
 * a missing browser runtime costs an interface and not the client.
 */
class WebSupportTest {

    @BeforeEach
    void reset() {
        WebRuntime.shutdown();
        // <b>A manifest without an address.</b> The real one in the classpath names
        // one since delivery — and the reach for the runtime environment
        // would thereby trigger a real download out of the test run, into
        // Gradle's working directory. Without an address it ends in the state
        // that this test run wants to see.
        Properties offline = new Properties();
        offline.setProperty("runtime.version", "0.0-probe");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".archive", "probe.tar.gz");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".sha256", "0".repeat(64));
        RuntimeManifest.useForTests(offline);
    }

    @AfterEach
    void restore() {
        WebRuntime.shutdown();
        RuntimeManifest.useForTests(null);
    }

    @Test
    @DisplayName("Without a runtime environment there is a state instead of a crash")
    void withoutRuntimeThereIsAnAnswerInsteadOfACrash() {
        WebRuntimeStatus status = WebSupport.ensureStarted();

        assertNotNull(status);
        assertFalse(status.usable(), "ohne Laufzeitumgebung kann nichts nutzbar sein");
        assertFalse(WebRuntime.isAvailable());

        // <b>The reason must arrive unchanged.</b> The reach for the
        // runtime environment does not find the folder and reports that as
        // RUNTIME_MISSING; whoever repackages it into FAILED along the way turns
        // „not there" into „broken" — and „is loading" likewise.
        assertEquals(WebRuntimeState.RUNTIME_MISSING, status.state(),
                "der Grund muss unverändert durchkommen: " + status);
    }

    @Test
    @DisplayName("And you can safely try again afterwards")
    void tryingAgainIsHarmless() {
        WebSupport.ensureStarted();
        WebRuntimeStatus second = WebSupport.retry();

        assertFalse(second.usable());
        assertFalse(WebRuntime.isAvailable());
    }
}
