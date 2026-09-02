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
 * Der Verwalter der offenen Browser.
 *
 * <p>Die Fehler, die diese Prüfläufe verhindern sollen, zeigen sich alle erst
 * beim Beenden des Spiels — also dort, wo niemand mehr hinsieht und eine
 * Ausnahme im Protokoll untergeht: eine Sitzung, die beim Schließen aus der
 * Schleife fällt; ein Warten auf eine Bestätigung, die längst da war; ein
 * zweiter Aufruf, der etwas kaputtmacht.
 *
 * <p><b>Warum sie hier stehen und nicht im Spiel gemessen werden.</b> Im Spiel
 * schließen die Bildschirme ihre Browser selbst, bevor das Herunterfahren
 * beginnt — {@code closeAll()} sah dort bisher immer eine leere Liste. Der
 * interessante Fall ist genau der andere.
 */
class BrowserManagerTest {

    /** Eine Sitzung, die aufschreibt, was mit ihr geschieht. */
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
            // Genau wie die echte Sitzung: erst abmelden, dann schließen.
            BrowserManager.closing(this);
            if (confirmOnClose) {
                // Chromium bestätigt sofort — im Spiel dauert es ein paar
                // Runden der Nachrichtenschleife.
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
    @DisplayName("Anmelden und abmelden zählt richtig")
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
    @DisplayName("closeAll schließt alles, obwohl sich jede Sitzung dabei abmeldet")
    void closeAllSurvivesSelfRemoval() {
        List<String> events = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BrowserManager.register(new FakeSession("s" + i, events, true));
        }
        assertEquals(5, BrowserManager.count());

        // Ohne Kopie flöge hier eine ConcurrentModificationException — jedes
        // close() nimmt sich selbst aus der Liste, über die gerade gelaufen
        // wird.
        BrowserManager.closeAll();

        assertEquals(0, BrowserManager.count());
        assertEquals(0, BrowserManager.pending());
        assertEquals(5, events.size(), "jede Sitzung genau einmal");
    }

    @Test
    @DisplayName("closeAll darf mehrfach gerufen werden")
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
    @DisplayName("Aus dem leeren Zustand wirft nichts")
    void emptyIsHarmless() {
        BrowserManager.closeAll();
        assertEquals(0, BrowserManager.count());
        assertTrue(BrowserManager.snapshot().isEmpty());
        assertTrue(BrowserManager.awaitClosed(50, () -> {
        }), "ohne Wartende ist sofort alles bestätigt");
    }

    @Test
    @DisplayName("Auf die Bestätigung wird gewartet — und das Pumpen bringt sie")
    void awaitClosedWaitsForConfirmation() {
        List<String> events = new ArrayList<>();
        // Diese Sitzung bestätigt nicht von selbst. Genau so verhält sich
        // Chromium: close(true) ist eine Bitte, onBeforeClose kommt aus der
        // Nachrichtenschleife.
        FakeSession slow = new FakeSession("langsam", events, false);
        BrowserManager.register(slow);
        BrowserManager.closeAll();

        assertEquals(0, BrowserManager.count());
        assertEquals(1, BrowserManager.pending(), "geschlossen, aber unbestätigt");

        int[] rounds = {0};
        boolean confirmed = BrowserManager.awaitClosed(1000, () -> {
            // Nach ein paar Runden meldet sich Chromium.
            if (++rounds[0] == 3) {
                BrowserManager.closed(slow);
            }
        });

        assertTrue(confirmed, "die Bestätigung kam beim Pumpen");
        assertEquals(0, BrowserManager.pending());
        assertTrue(rounds[0] >= 3);
    }

    @Test
    @DisplayName("Bleibt die Bestätigung aus, endet das Warten an der Frist")
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
    @DisplayName("Eine Sitzung, die beim Schließen wirft, hält die anderen nicht auf")
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
