package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What belongs together takes the same path.
 *
 * <p><b>The filter decides only on the press.</b> Everything that belongs to this
 * press — the character, the repeat, the release — follows it,
 * without asking the filter again. Otherwise the player starts moving and
 * keeps moving because the release landed at the page; or the page
 * gets a „w“ whose key the game already had.
 */
class KeyRoutingTest {

    private static final int ESCAPE = 256;
    private static final int UP = 265;
    private static final int W = 87;
    private static final int SHIFT = 0x0001;

    private final KeyRouting routing = new KeyRouting(KeyFilter.only(ESCAPE, UP));

    @Test
    @DisplayName("The character follows the key that was pressed before it")
    void aCharacterFollowsItsKey() {
        assertTrue(routing.press(UP, 0));
        assertTrue(routing.typed(), "die Seite hat die Taste, also auch das Zeichen");

        assertFalse(routing.press(W, 0));
        assertFalse(routing.typed(), "das Spiel hat die Taste, also auch das Zeichen");
    }

    @Test
    @DisplayName("The release follows the press, not the filter")
    void aReleaseFollowsItsPress() {
        assertFalse(routing.press(W, 0));
        assertFalse(routing.release(W), "das Spiel hat W gedrückt gesehen, es muss es auch losgelassen sehen");

        assertTrue(routing.press(UP, 0));
        assertTrue(routing.release(UP));
        assertFalse(routing.release(UP), "ein zweites Loslassen gehört niemandem mehr");
    }

    @Test
    @DisplayName("The repeat of a held key follows its press")
    void aRepeatFollowsItsPress() {
        // Whoever holds arrow-up and adds Shift still means
        // the same key — even if a filter saw the combination differently.
        KeyRouting picky = new KeyRouting((key, modifiers) -> key == UP && modifiers == 0);

        assertTrue(picky.press(UP, 0));
        assertTrue(picky.press(UP, SHIFT), "gehalten bleibt gehalten");
        assertTrue(picky.release(UP));
    }

    @Test
    @DisplayName("Without a prior press no character goes to the page")
    void noCharacterWithoutAPress() {
        assertFalse(routing.typed());
    }

    @Test
    @DisplayName("Releasing everything forgets every held press")
    void releaseAllForgetsEverything() {
        routing.press(UP, 0);
        routing.releaseAll();

        assertFalse(routing.release(UP));
        assertFalse(routing.typed());
    }
}
