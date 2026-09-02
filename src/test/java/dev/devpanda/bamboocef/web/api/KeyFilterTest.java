package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Welche Tasten eine Fläche bekommt.
 *
 * <p><b>Warum das mehr als eine Fallunterscheidung ist.</b> Ein Overlay, das
 * jede Taste nimmt, macht den Spieler bewegungsunfähig, solange es offen ist.
 * Eines, das keine nimmt, lässt sich nicht schließen. Dazwischen liegt der
 * übliche Fall, und der ist der Grund für diese Klasse.
 */
class KeyFilterTest {

    // Die Zahlen von GLFW, damit der Prüflauf ohne Fenster läuft.
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
    @DisplayName("Ein Editor nimmt alles")
    void everythingGoesToAnEditor() {
        assertTrue(KeyFilter.ALL.routes(W, 0));
        assertTrue(KeyFilter.ALL.routes(ESCAPE, SHIFT));
    }

    @Test
    @DisplayName("Ein Bild zum Ansehen nimmt nichts")
    void nothingGoesToAPicture() {
        assertFalse(KeyFilter.NONE.routes(ESCAPE, 0));
        assertFalse(KeyFilter.NONE.routes(W, 0));
    }

    @Test
    @DisplayName("Ein Schnellmenü nimmt Escape und die Pfeile, sonst nichts")
    void aQuickMenuTakesOnlyWhatItNeeds() {
        KeyFilter menu = KeyFilter.only(ESCAPE, UP, DOWN);

        assertTrue(menu.routes(ESCAPE, 0));
        assertTrue(menu.routes(UP, 0));
        assertFalse(menu.routes(W, 0), "W gehört dem Spieler, sonst steht er still");
    }

    @Test
    @DisplayName("Umschalttasten ändern nichts an der Entscheidung")
    void modifiersDoNotChangeTheAnswer() {
        // Absichtlich: Wer Umschalt hält, meint dieselbe Taste. Eine Regel je
        // Kombination wäre eine Tabelle, die niemand vollständig hinbekommt.
        KeyFilter menu = KeyFilter.only(ESCAPE);

        assertTrue(menu.routes(ESCAPE, SHIFT));
        assertFalse(menu.routes(W, SHIFT));
    }

    @Test
    @DisplayName("Alles außer einer Taste")
    void everythingExceptOne() {
        KeyFilter editor = KeyFilter.except(ESCAPE);

        assertTrue(editor.routes(W, 0));
        assertFalse(editor.routes(ESCAPE, 0), "damit der Spieler herauskommt");
    }

    @Test
    @DisplayName("Zwei Filter lassen sich verbinden")
    void twoFiltersCombine() {
        KeyFilter both = KeyFilter.only(ESCAPE, UP, W).and(KeyFilter.except(W));

        assertTrue(both.routes(ESCAPE, 0));
        assertFalse(both.routes(W, 0), "das zweite Sieb hält es zurück");

        KeyFilter either = KeyFilter.only(ESCAPE).or(KeyFilter.only(UP));
        assertTrue(either.routes(UP, 0));
    }

    @Test
    @DisplayName("Ein Schnellmenü aus zwei Gruppen")
    void groupsCombineIntoAQuickMenu() {
        KeyFilter menu = Keys.CLOSE.or(Keys.ARROWS);

        assertTrue(menu.routes(ESCAPE, 0));
        assertTrue(menu.routes(UP, 0));
        assertFalse(menu.routes(W, 0));
    }

    @Test
    @DisplayName("Ein Eingabefeld nimmt, was Text bewegt und ändert")
    void textEntryTakesWhatMovesAndChangesText() {
        assertTrue(Keys.TEXT_ENTRY.routes(BACKSPACE, 0));
        assertTrue(Keys.TEXT_ENTRY.routes(UP, 0));
        assertTrue(Keys.TEXT_ENTRY.routes(ENTER, 0));
        assertFalse(Keys.TEXT_ENTRY.routes(ESCAPE, 0),
                "ob Escape das Feld schließt oder die Eingabe verwirft, "
                        + "entscheidet der Aufrufer");
    }

    @Test
    @DisplayName("Alles außer der Bewegung — der Spieler bleibt beweglich")
    void everythingButMovement() {
        assertTrue(Keys.NOT_MOVEMENT.routes(ESCAPE, 0));
        assertTrue(Keys.NOT_MOVEMENT.routes(UP, 0));
        assertFalse(Keys.NOT_MOVEMENT.routes(W, 0));
        assertFalse(Keys.NOT_MOVEMENT.routes(SPACE, 0));
    }

    @Test
    @DisplayName("Kürzel gelten nur mit Strg")
    void shortcutsNeedControl() {
        // Ein blankes C durchzulassen naehme dem Spieler den Buchstaben — und
        // dieselbe Taste ist ohne Strg etwas voellig anderes.
        assertTrue(Keys.SHORTCUTS.routes(C, CONTROL));
        assertFalse(Keys.SHORTCUTS.routes(C, 0));
        assertFalse(Keys.SHORTCUTS.routes(W, CONTROL));
    }

    @Test
    @DisplayName("Bereiche: Funktionstasten und Schnellleiste")
    void rangesCoverTheirEnds() {
        assertTrue(Keys.FUNCTION.routes(F1, 0));
        assertTrue(Keys.FUNCTION.routes(F12, 0), "beide Enden gehören dazu");
        assertFalse(Keys.FUNCTION.routes(ESCAPE, 0));
        assertTrue(Keys.HOTBAR.routes(ONE, 0));
    }

    @Test
    @DisplayName("Eine Gruppe plus eine Taste, und eine Gruppe minus einer")
    void plusAndMinusAdjustAGroup() {
        assertTrue(Keys.ARROWS.plus(ENTER).routes(ENTER, 0));
        assertTrue(Keys.ARROWS.plus(ENTER).routes(UP, 0));

        KeyFilter editor = KeyFilter.ALL.minus(ESCAPE);
        assertTrue(editor.routes(W, 0));
        assertFalse(editor.routes(ESCAPE, 0), "damit der Spieler herauskommt");
    }

    @Test
    @DisplayName("Das Gegenteil eines Filters")
    void negateFlipsTheAnswer() {
        assertTrue(Keys.MOVEMENT.negate().routes(ESCAPE, 0));
        assertFalse(Keys.MOVEMENT.negate().routes(W, 0));
    }

    @Test
    @DisplayName("Der Bauplan begrenzt die Größe, statt sie zu glauben")
    void theSpecClampsItsSize() {
        // Eine Null ergäbe einen Browser, der nie malt; eine sehr große einen,
        // der den Speicher der Grafikkarte frisst. Beides fällt sonst erst
        // sehr viel später auf.
        assertEquals(1, SurfaceSpec.of("https://example.org", 0, 0).width());
        assertEquals(SurfaceSpec.MAX_EDGE,
                SurfaceSpec.of("https://example.org", 99_999, 512).width());
    }

    @Test
    @DisplayName("Ohne Adresse gibt es keinen Bauplan")
    void aSpecNeedsAnAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceSpec.of("  ", 512, 512));
    }

    @Test
    @DisplayName("Ohne Angabe nimmt eine Fläche keine Tasten")
    void aSpecTakesNoKeysUnlessAsked() {
        // Die sichere Vorgabe: Wer nichts sagt, macht den Spieler nicht
        // bewegungsunfähig.
        SurfaceSpec spec = SurfaceSpec.of("https://example.org", 512, 512);

        assertFalse(spec.keys().routes(W, 0));
        assertTrue(spec.keys(KeyFilter.ALL).keys().routes(W, 0));
    }
}
