package dev.devpanda.bamboocef.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.devpanda.bamboocef.web.runtime.WebWarmup;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The warm-up starts the runtime only on request, only after its delay, and
 * only once. Runs against a manifest without an address so that the start
 * attempt ends in RUNTIME_MISSING instead of a download — the same setup as
 * {@link WebSupportTest}, which is why this test lives in this package.
 */
class WebWarmupTest {

    @BeforeEach
    void reset() {
        WebRuntime.shutdown();
        WebWarmup.resetForTests();
        Properties offline = new Properties();
        offline.setProperty("runtime.version", "0.0-probe");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".archive", "probe.tar.gz");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".sha256", "0".repeat(64));
        RuntimeManifest.useForTests(offline);
    }

    @AfterEach
    void restore() {
        WebRuntime.shutdown();
        WebWarmup.resetForTests();
        RuntimeManifest.useForTests(null);
    }

    @Test
    @DisplayName("Without a request the ticks do nothing")
    void withoutRequestNothingHappens() {
        for (int i = 0; i < WebWarmup.DELAY_TICKS + 50; i++) {
            WebWarmup.tick();
        }
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
        assertFalse(WebWarmup.pending());
    }

    @Test
    @DisplayName("After the delay the runtime is started, exactly once")
    void afterTheDelayItStartsOnce() {
        WebWarmup.request();
        assertTrue(WebWarmup.pending());
        for (int i = 0; i < WebWarmup.DELAY_TICKS - 1; i++) {
            WebWarmup.tick();
        }
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state(),
                "one tick short of the delay nothing may have started");
        WebWarmup.tick();
        assertEquals(WebRuntimeState.RUNTIME_MISSING, WebRuntime.status().state(),
                "the start attempt must have run and ended in the offline state");
        assertFalse(WebWarmup.pending());
        for (int i = 0; i < 10; i++) {
            WebWarmup.tick();
        }
        assertFalse(WebWarmup.pending(), "a finished warm-up stays finished");
    }
}
