package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing on the runtime throws — every path ends in a state.
 *
 * <p>That is the guarantee under which a browser runtime may get into
 * this mod at all. A test run for it is cheap and would be expensive to catch up on:
 * the case you do not test is the one where someone's client no longer
 * starts.
 *
 * <p>Chromium is never touched here. The backend comes in as a lambda, and
 * exactly for that reason this layer is testable without Minecraft — starting Chromium in an
 * ordinary test run would be neither possible nor sensible.
 */
class WebRuntimeTest {

    /** A backend that does nothing but count along. */
    private static final class FakeBackend implements WebBackend {
        private final AtomicInteger closes = new AtomicInteger();
        private final RuntimeException failOnClose;

        FakeBackend() {
            this(null);
        }

        FakeBackend(RuntimeException failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public String name() {
            return "Attrappe";
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            if (failOnClose != null) {
                throw failOnClose;
            }
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        WebRuntime.shutdown();
    }

    @Test
    @DisplayName("Before the first attempt nothing has happened")
    void beforeAnythingHappens() {
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
        assertFalse(WebRuntime.isAvailable());
        assertNull(WebRuntime.backend());
    }

    @Test
    @DisplayName("A backend that comes up makes the runtime usable")
    void aWorkingBackendMakesItUsable() {
        FakeBackend backend = new FakeBackend();
        WebRuntimeStatus status = WebRuntime.start(() -> backend);

        assertEquals(WebRuntimeState.READY, status.state());
        assertTrue(WebRuntime.isAvailable());
        assertEquals(backend, WebRuntime.backend());
    }

    @Test
    @DisplayName("A foreseen reason comes through unchanged")
    void aKnownReasonSurvives() {
        WebRuntimeStatus status = WebRuntime.start(() -> {
            throw new WebRuntimeUnavailable(WebRuntimeState.RUNTIME_MISSING,
                    "Die Laufzeitumgebung liegt nicht neben dem Spiel");
        });

        assertEquals(WebRuntimeState.RUNTIME_MISSING, status.state());
        assertEquals("Die Laufzeitumgebung liegt nicht neben dem Spiel", status.reason());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Even a NoClassDefFoundError only brings back a state")
    void evenAnErrorIsJustAState() {
        // Exactly this happens when the runtime environment is missing and yet
        // someone touches a class from it: not an Exception, but an
        // Error. Whoever catches only Exception takes the client down with it.
        WebRuntimeStatus status = WebRuntime.start(() -> {
            throw new NoClassDefFoundError("org/cef/CefApp");
        });

        assertEquals(WebRuntimeState.FAILED, status.state());
        assertTrue(status.reason().contains("NoClassDefFoundError"),
                "der Grund muss im Text stehen, sonst sucht jemand blind");
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("A backend that silently returns null counts as failed")
    void aSilentNullCounts() {
        WebRuntimeStatus status = WebRuntime.start(() -> null);

        assertEquals(WebRuntimeState.FAILED, status.state());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("Starting twice tries only once")
    void startingTwiceTriesOnce() {
        AtomicInteger attempts = new AtomicInteger();
        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });
        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });

        assertEquals(1, attempts.get(),
                "sonst baut jeder Aufruf einen zweiten Browserprozess auf");
    }

    @Test
    @DisplayName("A second attempt is worthwhile only if the reason can change")
    void retryingOnlyWhereItCanHelp() {
        AtomicInteger attempts = new AtomicInteger();

        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            throw new WebRuntimeUnavailable(WebRuntimeState.UNSUPPORTED, "keine Binärdateien");
        });
        WebRuntime.retry(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });

        assertEquals(1, attempts.get(),
                "eine fehlende Plattform taucht auch beim zehnten Mal nicht auf");
        assertEquals(WebRuntimeState.UNSUPPORTED, WebRuntime.status().state());
    }

    @Test
    @DisplayName("After shutdown that is the end — no second start")
    void afterShutdownNothingRestarts() {
        // The backend reports the final state; the runtime must not take it as
        // occasion for another attempt. When the game shuts down
        // exactly this call comes once more from the render path.
        AtomicInteger attempts = new AtomicInteger();

        WebRuntime.start(() -> {
            attempts.incrementAndGet();
            throw new WebRuntimeUnavailable(WebRuntimeState.SHUT_DOWN, "Chromium ist unten");
        });
        WebRuntime.retry(() -> {
            attempts.incrementAndGet();
            return new FakeBackend();
        });

        assertEquals(1, attempts.get(), "CEF startet je Prozess nur einmal");
        assertEquals(WebRuntimeState.SHUT_DOWN, WebRuntime.status().state());
        assertFalse(WebRuntime.status().state().worthRetrying());
    }

    @Test
    @DisplayName("After a failure you may try again")
    void afterAFailureRetryingWorks() {
        WebRuntime.start(() -> {
            throw new WebRuntimeUnavailable(WebRuntimeState.NOT_DOWNLOADED, "kein Netz");
        });
        assertTrue(WebRuntime.status().state().worthRetrying());

        WebRuntimeStatus second = WebRuntime.retry(FakeBackend::new);
        assertEquals(WebRuntimeState.READY, second.state(),
                "ein nachgeholter Download muss ohne Neustart wirken können");
    }

    @Test
    @DisplayName("Shutdown closes the backend and resets")
    void shutdownReleasesEverything() {
        FakeBackend backend = new FakeBackend();
        WebRuntime.start(() -> backend);
        WebRuntime.shutdown();

        assertEquals(1, backend.closes.get());
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
        assertFalse(WebRuntime.isAvailable());
    }

    @Test
    @DisplayName("A backend that throws on closing drags nothing down with it")
    void aThrowingCloseIsContained() {
        FakeBackend angry = new FakeBackend(new IllegalStateException("kaputt"));
        WebRuntime.start(() -> angry);

        WebRuntime.shutdown();

        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state(),
                "beim Beenden des Spiels ist eine Ausnahme aus einem Browser "
                        + "das Letzte, was jemand sehen will");
    }

    @Test
    @DisplayName("Shutdown without a start does nothing")
    void shutdownWithoutStartIsHarmless() {
        WebRuntime.shutdown();
        assertEquals(WebRuntimeState.NOT_STARTED, WebRuntime.status().state());
    }
}
