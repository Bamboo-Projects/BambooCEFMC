package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Taktgeber: reine Zeitrechnung, deshalb hier prüfbar.
 *
 * <p>Die Uhr kommt von außen. Ein Prüflauf, der auf echte Zeit wartet, ist
 * langsam und launisch; einer, der sie stellt, prüft dieselbe Rechnung in
 * Mikrosekunden.
 */
class FramePacerTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    @DisplayName("Das erste Bild ist sofort fällig")
    void theFirstFrameIsDueAtOnce() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);
        assertTrue(pacer.due(0L), "sonst bleibt eine frisch geöffnete Fläche leer");
    }

    @Test
    @DisplayName("Danach erst wieder nach dem Abstand")
    void thenOnlyAfterTheInterval() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);   // 30/s
        pacer.drawn(0L);

        assertFalse(pacer.due(SECOND / 60), "eine Sechzigstelsekunde ist zu früh");
        assertTrue(pacer.due(SECOND / 30), "eine Dreißigstel genau reicht");
        assertTrue(pacer.due(SECOND), "und später sowieso");
    }

    @Test
    @DisplayName("Wer unsichtbar ist, malt nie")
    void hiddenMeansNever() {
        FramePacer pacer = new FramePacer(BrowserVisibility.HIDDEN);

        assertFalse(pacer.due(0L));
        assertFalse(pacer.due(SECOND * 3600));
        assertEquals(-1L, BrowserVisibility.HIDDEN.frameIntervalNanos());
        assertFalse(BrowserVisibility.HIDDEN.drawing());
    }

    @Test
    @DisplayName("Wer sichtbar wird, malt sofort")
    void wakingUpDrawsAtOnce() {
        FramePacer pacer = new FramePacer(BrowserVisibility.HIDDEN);
        pacer.drawn(0L);

        pacer.setVisibility(BrowserVisibility.FOREGROUND);
        assertTrue(pacer.due(1L),
                "sonst sieht der Spieler beim Aufklappen das alte Bild");
    }

    @Test
    @DisplayName("Der Takt holt nichts nach")
    void theClockDoesNotCatchUp() {
        FramePacer pacer = new FramePacer(BrowserVisibility.DISTANT);   // 5/s
        pacer.drawn(0L);

        // Eine Minute später: fällig, aber nur einmal — nicht dreihundertmal.
        long muchLater = SECOND * 60;
        assertTrue(pacer.due(muchLater));
        pacer.drawn(muchLater);
        assertFalse(pacer.due(muchLater + 1),
                "ein Browser, der zurückkommt, holt keine Vergangenheit nach");
    }

    @Test
    @DisplayName("Ein ausgefallenes Bild lässt den Takt stehen")
    void askipDoesNotAdvanceTheClock() {
        FramePacer pacer = new FramePacer(BrowserVisibility.ACTIVE);
        pacer.drawn(0L);
        pacer.skipped();

        assertTrue(pacer.due(1L),
                "wer gefragt wurde und nichts lieferte, darf gleich wieder gefragt werden");
    }

    @Test
    @DisplayName("Ohne Angabe gilt: unsichtbar")
    void nullIsHidden() {
        FramePacer pacer = new FramePacer(null);
        assertEquals(BrowserVisibility.HIDDEN, pacer.visibility());

        pacer.setVisibility(null);
        assertEquals(BrowserVisibility.HIDDEN, pacer.visibility());
    }

    @Test
    @DisplayName("Die Abstände passen zu den Bildraten")
    void intervalsMatchTheRates() {
        assertEquals(SECOND / 60, BrowserVisibility.FOREGROUND.frameIntervalNanos());
        assertEquals(SECOND / 30, BrowserVisibility.ACTIVE.frameIntervalNanos());
        assertEquals(SECOND / 15, BrowserVisibility.NEARBY.frameIntervalNanos());
        assertEquals(SECOND / 5, BrowserVisibility.DISTANT.frameIntervalNanos());
    }
}
