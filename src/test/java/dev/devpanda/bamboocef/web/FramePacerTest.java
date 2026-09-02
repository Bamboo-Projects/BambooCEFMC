package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pacemaker: pure time arithmetic, therefore testable here.
 *
 * <p>The clock comes from outside. A test run that waits for real time is
 * slow and moody; one that sets it checks the same calculation in
 * microseconds.
 */
class FramePacerTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    @DisplayName("The first frame is due at once")
    void theFirstFrameIsDueAtOnce() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);
        assertTrue(pacer.due(0L), "sonst bleibt eine frisch geöffnete Fläche leer");
    }

    @Test
    @DisplayName("After that only again after the interval")
    void thenOnlyAfterTheInterval() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);   // 30/s
        pacer.drawn(0L);

        assertFalse(pacer.due(SECOND / 60), "eine Sechzigstelsekunde ist zu früh");
        assertTrue(pacer.due(SECOND / 30), "eine Dreißigstel genau reicht");
        assertTrue(pacer.due(SECOND), "und später sowieso");
    }

    @Test
    @DisplayName("Whoever is invisible never draws")
    void hiddenMeansNever() {
        FramePacer pacer = new FramePacer(BrowserVisibility.HIDDEN);

        assertFalse(pacer.due(0L));
        assertFalse(pacer.due(SECOND * 3600));
        assertEquals(-1L, BrowserVisibility.HIDDEN.frameIntervalNanos());
        assertFalse(BrowserVisibility.HIDDEN.drawing());
    }

    @Test
    @DisplayName("Whoever becomes visible draws at once")
    void wakingUpDrawsAtOnce() {
        FramePacer pacer = new FramePacer(BrowserVisibility.HIDDEN);
        pacer.drawn(0L);

        pacer.setVisibility(BrowserVisibility.FOREGROUND);
        assertTrue(pacer.due(1L),
                "sonst sieht der Spieler beim Aufklappen das alte Bild");
    }

    @Test
    @DisplayName("The clock does not catch up")
    void theClockDoesNotCatchUp() {
        FramePacer pacer = new FramePacer(BrowserVisibility.DISTANT);   // 5/s
        pacer.drawn(0L);

        // A minute later: due, but only once — not three hundred times.
        long muchLater = SECOND * 60;
        assertTrue(pacer.due(muchLater));
        pacer.drawn(muchLater);
        assertFalse(pacer.due(muchLater + 1),
                "ein Browser, der zurückkommt, holt keine Vergangenheit nach");
    }

    @Test
    @DisplayName("A dropped frame leaves the clock standing")
    void askipDoesNotAdvanceTheClock() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);
        pacer.drawn(0L);
        pacer.skipped();

        assertTrue(pacer.due(1L),
                "wer gefragt wurde und nichts lieferte, darf gleich wieder gefragt werden");
    }

    @Test
    @DisplayName("With no value given: invisible")
    void nullIsHidden() {
        FramePacer pacer = new FramePacer(null);
        assertEquals(BrowserVisibility.HIDDEN, pacer.visibility());

        pacer.setVisibility(null);
        assertEquals(BrowserVisibility.HIDDEN, pacer.visibility());
    }

    @Test
    @DisplayName("The intervals match the frame rates")
    void intervalsMatchTheRates() {
        assertEquals(SECOND / 60, BrowserVisibility.FOREGROUND.frameIntervalNanos());
        assertEquals(SECOND / 30, BrowserVisibility.ACTIVE.frameIntervalNanos());
        assertEquals(SECOND / 15, BrowserVisibility.NEARBY.frameIntervalNanos());
        assertEquals(SECOND / 5, BrowserVisibility.DISTANT.frameIntervalNanos());
    }
}
