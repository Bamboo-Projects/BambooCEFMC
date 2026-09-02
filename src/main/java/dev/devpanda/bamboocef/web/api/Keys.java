package dev.devpanda.bamboocef.web.api;

import org.lwjgl.glfw.GLFW;

/**
 * Fertige Tastengruppen für {@link KeyFilter}.
 *
 * <p><b>Warum es die gibt.</b> Ein Schnellmenü braucht „die Pfeiltasten", ein
 * Formular „alles, was Text macht", ein Overlay „alles außer Bewegung". Ohne
 * Gruppen schreibt jeder dieselbe Zahlenliste neu — und jeder vergisst eine
 * andere Taste darin.
 *
 * <pre>
 * Keys.CLOSE.or(Keys.ARROWS)          ein Schnellmenü
 * Keys.TEXT_ENTRY                      ein Eingabefeld
 * KeyFilter.ALL.and(Keys.NOT_MOVEMENT) alles, aber der Spieler bleibt beweglich
 * </pre>
 *
 * <p>Die Gruppen sind Filter und keine Listen: Sie lassen sich mit
 * {@link KeyFilter#and} und {@link KeyFilter#or} verbinden, ohne dass jemand
 * Zahlenfelder zusammenfügen muss.
 */
public final class Keys {

    private Keys() {
    }

    /** Escape — der Weg hinaus. Gehört in fast jedes Overlay. */
    public static final KeyFilter CLOSE = KeyFilter.only(GLFW.GLFW_KEY_ESCAPE);

    /** Die vier Pfeiltasten. */
    public static final KeyFilter ARROWS = KeyFilter.only(
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT);

    /** Eingabetaste und die des Ziffernblocks. */
    public static final KeyFilter CONFIRM = KeyFilter.only(
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER);

    /**
     * Was durch eine Liste führt: Pfeile, Bild auf und ab, Anfang und Ende,
     * dazu Tabulator.
     */
    public static final KeyFilter NAVIGATION = KeyFilter.only(
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN,
            GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END,
            GLFW.GLFW_KEY_TAB);

    /** Was Text ändert, ohne selbst eines zu sein: Rücktaste, Entfernen, Einfügen. */
    public static final KeyFilter EDITING = KeyFilter.only(
            GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_INSERT);

    /**
     * Ein Eingabefeld: Buchstaben und Zahlen kommen als Zeichen, hier stehen
     * die Tasten drumherum.
     *
     * <p>Ohne {@link #CLOSE} — wer ein Feld baut, entscheidet selbst, ob
     * Escape es schließt oder die Eingabe verwirft.
     */
    public static final KeyFilter TEXT_ENTRY = NAVIGATION.or(EDITING).or(CONFIRM);

    /** Die zwölf Funktionstasten. */
    public static final KeyFilter FUNCTION = filterRange(
            GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_F12);

    /** Die Zifferntasten der obersten Reihe — die Schnellleiste des Spiels. */
    public static final KeyFilter HOTBAR = filterRange(
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_9);

    /**
     * Womit ein Spieler sich bewegt: W, A, S, D, Leertaste, Umschalt links,
     * Strg links.
     *
     * <p>Selten als Erlaubnis gemeint und fast immer als das Gegenteil —
     * dafür gibt es {@link #NOT_MOVEMENT}.
     */
    public static final KeyFilter MOVEMENT = KeyFilter.only(
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL);

    /**
     * Alles außer der Bewegung.
     *
     * <p>Der übliche Wunsch bei einem Overlay, das lange offen bleibt: Die
     * Fläche darf tippen lassen, aber der Spieler soll nicht feststecken.
     */
    public static final KeyFilter NOT_MOVEMENT = KeyFilter.except(
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL);

    /**
     * Was mit Strg zusammen ein Kürzel ergibt: C, V, X, A, S, F, Z, Y.
     *
     * <p><b>Nur mit gedrücktem Strg.</b> Ein Filter, der ein blankes C
     * durchließe, nähme dem Spieler den Buchstaben — und dieselbe Taste ist
     * ohne Strg etwas völlig anderes.
     */
    public static final KeyFilter SHORTCUTS = (key, modifiers) -> {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            return false;
        }
        return switch (key) {
            case GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_X,
                 GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_F,
                 GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y -> true;
            default -> false;
        };
    };

    /**
     * Ein Editor: alles, was eine Tastatur hergibt.
     *
     * <p>Dasselbe wie {@link KeyFilter#ALL} und trotzdem ein eigener Name — er
     * sagt, warum es alles ist.
     */
    public static final KeyFilter EDITOR = KeyFilter.ALL;

    /** Ein zusammenhängender Bereich von Tastennummern, beide Enden dabei. */
    private static KeyFilter filterRange(int first, int last) {
        return (key, modifiers) -> key >= first && key <= last;
    }
}
