package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The manager of the open browsers.
 *
 * <p>The faults these test runs are meant to prevent all show up only
 * when the game shuts down — that is, where no one is looking anymore and an
 * exception is lost in the log: a session that falls out of the loop
 * on closing; a wait for a confirmation that was long since there; a
 * second call that breaks something.
 *
 * <p><b>Why they stand here and are not measured in the game.</b> In the game
 * the screens close their browsers themselves before the shutdown
 * begins — {@code closeAll()} always saw an empty list there so far. The
 * interesting case is exactly the other one.
 */
class BrowserManagerTest {

    /** A session that writes down what happens to it. */
    private static final class FakeSession implements ManagedBrowser {
        private final String name;
        private final List<String> events;
        private final boolean confirmOnClose;
        private boolean closed;

        FakeSession(String name, List<String> events, boolean confirmOnClose) {
            this.name = name;
            this.events = events;
            this.confirmOnClose = confirmOnClose;
        }

        @Override
        public void close() {
            if (closed) {
                events.add(name + ": zweites close");
                return;
            }
            closed = true;
            events.add(name + ": close");
            // Exactly like the real session: first unregister, then close.
            BrowserManager.closing(this);
            if (confirmOnClose) {
                // Chromium confirms at once — in the game it takes a few
                // rounds of the message loop.
                BrowserManager.closed(this);
            }
        }

        @Override
        public String describe() {
            return name;
        }
    }

    @BeforeEach
    void clean() {
        BrowserManager.forgetAll();
    }

    @Test
    @DisplayName("Registering and unregistering counts correctly")
    void countFollowsRegistration() {
        List<String> events = new ArrayList<>();
        FakeSession one = new FakeSession("eins", events, true);
        FakeSession two = new FakeSession("zwei", events, true);

        assertEquals(0, BrowserManager.count());
        BrowserManager.register(one);
        BrowserManager.register(two);
        assertEquals(2, BrowserManager.count());

        one.close();
        assertEquals(1, BrowserManager.count(), "geschlossen heißt nicht mehr offen");
        assertEquals(0, BrowserManager.pending(), "und bestätigt heißt: ganz weg");
    }

    @Test
    @DisplayName("closeAll closes everything, even though each session unregisters itself in the process")
    void closeAllSurvivesSelfRemoval() {
        List<String> events = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BrowserManager.register(new FakeSession("s" + i, events, true));
        }
        assertEquals(5, BrowserManager.count());

        // Without a copy a ConcurrentModificationException would fly here — every
        // close() removes itself from the list that is currently being iterated
        // over.
        BrowserManager.closeAll();

        assertEquals(0, BrowserManager.count());
        assertEquals(0, BrowserManager.pending());
        assertEquals(5, events.size(), "jede Sitzung genau einmal");
    }

    @Test
    @DisplayName("closeAll may be called multiple times")
    void closeAllIsIdempotent() {
        List<String> events = new ArrayList<>();
        BrowserManager.register(new FakeSession("eins", events, true));

        BrowserManager.closeAll();
        BrowserManager.closeAll();
        BrowserManager.closeAll();

        assertEquals(1, events.size(), "beim zweiten Mal gibt es nichts mehr zu schließen");
        assertEquals(0, BrowserManager.count());
    }

    @Test
    @DisplayName("From the empty state nothing throws")
    void emptyIsHarmless() {
        BrowserManager.closeAll();
        assertEquals(0, BrowserManager.count());
        assertTrue(BrowserManager.snapshot().isEmpty());
        assertTrue(BrowserManager.awaitClosed(50, () -> {
        }), "ohne Wartende ist sofort alles bestätigt");
    }

    @Test
    @DisplayName("The confirmation is waited for — and pumping brings it")
    void awaitClosedWaitsForConfirmation() {
        List<String> events = new ArrayList<>();
        // This session does not confirm on its own. That is exactly how
        // Chromium behaves: close(true) is a request, onBeforeClose comes from the
        // message loop.
        FakeSession slow = new FakeSession("langsam", events, false);
        BrowserManager.register(slow);
        BrowserManager.closeAll();

        assertEquals(0, BrowserManager.count());
        assertEquals(1, BrowserManager.pending(), "geschlossen, aber unbestätigt");

        int[] rounds = {0};
        boolean confirmed = BrowserManager.awaitClosed(1000, () -> {
            // After a few rounds Chromium reports back.
            if (++rounds[0] == 3) {
                BrowserManager.closed(slow);
            }
        });

        assertTrue(confirmed, "die Bestätigung kam beim Pumpen");
        assertEquals(0, BrowserManager.pending());
        assertTrue(rounds[0] >= 3);
    }

    @Test
    @DisplayName("If the confirmation fails to come, the waiting ends at the deadline")
    void awaitClosedGivesUp() {
        List<String> events = new ArrayList<>();
        BrowserManager.register(new FakeSession("stumm", events, false));
        BrowserManager.closeAll();

        long before = System.currentTimeMillis();
        boolean confirmed = BrowserManager.awaitClosed(120, () -> {
        });
        long waited = System.currentTimeMillis() - before;

        assertFalse(confirmed, "ohne Bestätigung darf es kein Ja geben");
        assertEquals(1, BrowserManager.pending());
        assertTrue(waited < 2000, "die Frist hält, sonst hinge das Spiel");
        assertTrue(BrowserManager.snapshot().stream().anyMatch(line -> line.contains("stumm")),
                "wer fehlt, gehört ins Protokoll");
    }

    @Test
    @DisplayName("A session that throws on closing does not hold up the others")
    void oneBrokenSessionDoesNotStopTheRest() {
        List<String> events = new ArrayList<>();
        BrowserManager.register(new FakeSession("vorher", events, true));
        BrowserManager.register(new ManagedBrowser() {
            @Override
            public void close() {
                throw new IllegalStateException("kaputt");
            }

            @Override
            public String describe() {
                return "kaputt";
            }
        });
        BrowserManager.register(new FakeSession("danach", events, true));

        BrowserManager.closeAll();

        assertEquals(2, events.size(), "beide gesunden Sitzungen wurden geschlossen");
        assertEquals(0, BrowserManager.count(), "auch die kaputte ist aus der Liste");
    }
}
