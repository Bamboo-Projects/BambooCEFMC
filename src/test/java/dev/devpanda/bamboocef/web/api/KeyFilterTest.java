package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which keys a surface receives.
 *
 * <p><b>Why this is more than a case distinction.</b> An overlay that
 * takes every key leaves the player unable to move as long as it is open.
 * One that takes none cannot be closed. Between them lies the
 * usual case, and that is the reason for this class.
 */
class KeyFilterTest {

    // The numbers from GLFW, so that the test run runs without a window.
    private static final int ESCAPE = 256;
    private static final int UP = 265;
    private static final int DOWN = 264;
    private static final int W = 87;
    private static final int SHIFT = 0x0001;
    private static final int CONTROL = 0x0002;
    private static final int SPACE = 32;
    private static final int C = 67;
    private static final int ENTER = 257;
    private static final int BACKSPACE = 259;
    private static final int F1 = 290;
    private static final int F12 = 301;
    private static final int ONE = 49;

    @Test
    @DisplayName("An editor takes everything")
    void everythingGoesToAnEditor() {
        assertTrue(KeyFilter.ALL.routes(W, 0));
        assertTrue(KeyFilter.ALL.routes(ESCAPE, SHIFT));
    }

    @Test
    @DisplayName("A picture just to look at takes nothing")
    void nothingGoesToAPicture() {
        assertFalse(KeyFilter.NONE.routes(ESCAPE, 0));
        assertFalse(KeyFilter.NONE.routes(W, 0));
    }

    @Test
    @DisplayName("A quick menu takes Escape and the arrows, nothing else")
    void aQuickMenuTakesOnlyWhatItNeeds() {
        KeyFilter menu = KeyFilter.only(ESCAPE, UP, DOWN);

        assertTrue(menu.routes(ESCAPE, 0));
        assertTrue(menu.routes(UP, 0));
        assertFalse(menu.routes(W, 0), "W gehört dem Spieler, sonst steht er still");
    }

    @Test
    @DisplayName("Modifier keys change nothing about the decision")
    void modifiersDoNotChangeTheAnswer() {
        // On purpose: whoever holds Shift means the same key. One rule per
        // combination would be a table that no one gets complete.
        KeyFilter menu = KeyFilter.only(ESCAPE);

        assertTrue(menu.routes(ESCAPE, SHIFT));
        assertFalse(menu.routes(W, SHIFT));
    }

    @Test
    @DisplayName("Everything except one key")
    void everythingExceptOne() {
        KeyFilter editor = KeyFilter.except(ESCAPE);

        assertTrue(editor.routes(W, 0));
        assertFalse(editor.routes(ESCAPE, 0), "damit der Spieler herauskommt");
    }

    @Test
    @DisplayName("Two filters can be combined")
    void twoFiltersCombine() {
        KeyFilter both = KeyFilter.only(ESCAPE, UP, W).and(KeyFilter.except(W));

        assertTrue(both.routes(ESCAPE, 0));
        assertFalse(both.routes(W, 0), "das zweite Sieb hält es zurück");

        KeyFilter either = KeyFilter.only(ESCAPE).or(KeyFilter.only(UP));
        assertTrue(either.routes(UP, 0));
    }

    @Test
    @DisplayName("A quick menu from two groups")
    void groupsCombineIntoAQuickMenu() {
        KeyFilter menu = Keys.CLOSE.or(Keys.ARROWS);

        assertTrue(menu.routes(ESCAPE, 0));
        assertTrue(menu.routes(UP, 0));
        assertFalse(menu.routes(W, 0));
    }

    @Test
    @DisplayName("A text field takes what moves and changes text")
    void textEntryTakesWhatMovesAndChangesText() {
        assertTrue(Keys.TEXT_ENTRY.routes(BACKSPACE, 0));
        assertTrue(Keys.TEXT_ENTRY.routes(UP, 0));
        assertTrue(Keys.TEXT_ENTRY.routes(ENTER, 0));
        assertFalse(Keys.TEXT_ENTRY.routes(ESCAPE, 0),
                "ob Escape das Feld schließt oder die Eingabe verwirft, "
                        + "entscheidet der Aufrufer");
    }

    @Test
    @DisplayName("Everything but movement — the player stays mobile")
    void everythingButMovement() {
        assertTrue(Keys.NOT_MOVEMENT.routes(ESCAPE, 0));
        assertTrue(Keys.NOT_MOVEMENT.routes(UP, 0));
        assertFalse(Keys.NOT_MOVEMENT.routes(W, 0));
        assertFalse(Keys.NOT_MOVEMENT.routes(SPACE, 0));
    }

    @Test
    @DisplayName("Shortcuts apply only with Ctrl")
    void shortcutsNeedControl() {
        // Letting a bare C through would take the letter from the player — and
        // the same key without Ctrl is something completely different.
        assertTrue(Keys.SHORTCUTS.routes(C, CONTROL));
        assertFalse(Keys.SHORTCUTS.routes(C, 0));
        assertFalse(Keys.SHORTCUTS.routes(W, CONTROL));
    }

    @Test
    @DisplayName("Ranges: function keys and hotbar")
    void rangesCoverTheirEnds() {
        assertTrue(Keys.FUNCTION.routes(F1, 0));
        assertTrue(Keys.FUNCTION.routes(F12, 0), "beide Enden gehören dazu");
        assertFalse(Keys.FUNCTION.routes(ESCAPE, 0));
        assertTrue(Keys.HOTBAR.routes(ONE, 0));
    }

    @Test
    @DisplayName("A group plus one key, and a group minus one")
    void plusAndMinusAdjustAGroup() {
        assertTrue(Keys.ARROWS.plus(ENTER).routes(ENTER, 0));
        assertTrue(Keys.ARROWS.plus(ENTER).routes(UP, 0));

        KeyFilter editor = KeyFilter.ALL.minus(ESCAPE);
        assertTrue(editor.routes(W, 0));
        assertFalse(editor.routes(ESCAPE, 0), "damit der Spieler herauskommt");
    }

    @Test
    @DisplayName("The opposite of a filter")
    void negateFlipsTheAnswer() {
        assertTrue(Keys.MOVEMENT.negate().routes(ESCAPE, 0));
        assertFalse(Keys.MOVEMENT.negate().routes(W, 0));
    }

    @Test
    @DisplayName("The spec clamps the size instead of trusting it")
    void theSpecClampsItsSize() {
        // A zero would yield a browser that never draws; a very large one one
        // that devours the graphics card's memory. Both otherwise show up only
        // much later.
        assertEquals(1, SurfaceSpec.of("https://example.org", 0, 0).width());
        assertEquals(SurfaceSpec.MAX_EDGE,
                SurfaceSpec.of("https://example.org", 99_999, 512).width());
    }

    @Test
    @DisplayName("Without an address there is no spec")
    void aSpecNeedsAnAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceSpec.of("  ", 512, 512));
    }

    @Test
    @DisplayName("With no value given a surface takes no keys")
    void aSpecTakesNoKeysUnlessAsked() {
        // The safe default: whoever says nothing does not leave the player
        // unable to move.
        SurfaceSpec spec = SurfaceSpec.of("https://example.org", 512, 512);

        assertFalse(spec.keys().routes(W, 0));
        assertTrue(spec.keys(KeyFilter.ALL).keys().routes(W, 0));
    }
}
