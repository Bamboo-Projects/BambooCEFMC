package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was zusammengehört, geht denselben Weg.
 *
 * <p><b>Der Filter entscheidet nur beim Drücken.</b> Alles, was zu diesem
 * Druck gehört — das Zeichen, die Wiederholung, das Loslassen — folgt ihm,
 * ohne den Filter noch einmal zu fragen. Sonst läuft der Spieler los und
 * bleibt laufen, weil das Loslassen bei der Seite landete; oder die Seite
 * bekommt ein „w“, dessen Taste das Spiel schon hatte.
 */
class KeyRoutingTest {

    private static final int ESCAPE = 256;
    private static final int UP = 265;
    private static final int W = 87;
    private static final int SHIFT = 0x0001;

    private final KeyRouting routing = new KeyRouting(KeyFilter.only(ESCAPE, UP));

    @Test
    @DisplayName("Das Zeichen folgt der Taste, die davor gedrückt wurde")
    void aCharacterFollowsItsKey() {
        assertTrue(routing.press(UP, 0));
        assertTrue(routing.typed(), "die Seite hat die Taste, also auch das Zeichen");

        assertFalse(routing.press(W, 0));
        assertFalse(routing.typed(), "das Spiel hat die Taste, also auch das Zeichen");
    }

    @Test
    @DisplayName("Das Loslassen folgt dem Drücken, nicht dem Filter")
    void aReleaseFollowsItsPress() {
        assertFalse(routing.press(W, 0));
        assertFalse(routing.release(W), "das Spiel hat W gedrückt gesehen, es muss es auch losgelassen sehen");

        assertTrue(routing.press(UP, 0));
        assertTrue(routing.release(UP));
        assertFalse(routing.release(UP), "ein zweites Loslassen gehört niemandem mehr");
    }

    @Test
    @DisplayName("Die Wiederholung einer gehaltenen Taste folgt ihrem Drücken")
    void aRepeatFollowsItsPress() {
        // Wer Pfeil-hoch hält und dabei Umschalt dazunimmt, meint weiter
        // dieselbe Taste — auch wenn ein Filter die Kombination anders sähe.
        KeyRouting picky = new KeyRouting((key, modifiers) -> key == UP && modifiers == 0);

        assertTrue(picky.press(UP, 0));
        assertTrue(picky.press(UP, SHIFT), "gehalten bleibt gehalten");
        assertTrue(picky.release(UP));
    }

    @Test
    @DisplayName("Ohne vorheriges Drücken geht kein Zeichen an die Seite")
    void noCharacterWithoutAPress() {
        assertFalse(routing.typed());
    }

    @Test
    @DisplayName("Alles loslassen vergisst jeden gehaltenen Druck")
    void releaseAllForgetsEverything() {
        routing.press(UP, 0);
        routing.releaseAll();

        assertFalse(routing.release(UP));
        assertFalse(routing.typed());
    }
}
