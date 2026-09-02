package dev.devpanda.bamboocef.web.api;

import java.util.Arrays;

/**
 * Welche Tasten eine Fläche bekommt — und welche das Spiel behält.
 *
 * <p><b>Der Grund, warum es das gibt.</b> Ein Editor braucht jede Taste; ein
 * Schnellmenü braucht Escape und die Pfeiltasten, und W, A, S und D gehören
 * dem Spieler. Eine Fläche, die nur alles oder nichts bekommen kann, ist für
 * den zweiten Fall unbrauchbar — und der ist der häufigere.
 *
 * <p><b>Die Entscheidung fällt vor dem Weiterreichen, nicht danach.</b> Wer
 * eine Taste erst an Chromium schickt und dann hofft, dass Minecraft sie auch
 * noch sieht, bekommt beides halb: Die Seite reagiert, und der Spieler läuft
 * trotzdem los.
 *
 * <p>Die Tastennummern sind die von GLFW, dieselben, die Minecraft benutzt —
 * {@code GLFW_KEY_ESCAPE} und Verwandte. Absichtlich keine eigene Aufzählung:
 * Wer eine Fläche einbaut, hat die Zahlen ohnehin vor sich.
 */
@FunctionalInterface
public interface KeyFilter {

    /**
     * Bekommt die Fläche diese Taste?
     *
     * @param glfwKey   die Taste nach GLFW
     * @param modifiers gedrückte Umschalttasten, ebenfalls nach GLFW
     */
    boolean routes(int glfwKey, int modifiers);

    /** Alles geht an die Fläche — was ein Editor braucht. */
    KeyFilter ALL = (key, modifiers) -> true;

    /** Nichts geht an die Fläche. Sie ist dann ein Bild zum Ansehen. */
    KeyFilter NONE = (key, modifiers) -> false;

    /**
     * Nur diese Tasten, unabhängig von Umschalttasten.
     *
     * <pre>
     * KeyFilter.only(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN)
     * </pre>
     */
    static KeyFilter only(int... keys) {
        int[] allowed = keys.clone();
        Arrays.sort(allowed);
        return (key, modifiers) -> Arrays.binarySearch(allowed, key) >= 0;
    }

    /**
     * Alles außer diesen Tasten.
     *
     * <p>Für den Fall, dass eine Fläche fast alles will: Ein Editor, dem der
     * Spieler trotzdem mit Escape entkommen soll, ist
     * {@code KeyFilter.except(GLFW_KEY_ESCAPE)}.
     */
    static KeyFilter except(int... keys) {
        int[] blocked = keys.clone();
        Arrays.sort(blocked);
        return (key, modifiers) -> Arrays.binarySearch(blocked, key) < 0;
    }

    /** Dieser Filter <b>und</b> jener — eine Taste muss durch beide passen. */
    default KeyFilter and(KeyFilter other) {
        return (key, modifiers) -> routes(key, modifiers) && other.routes(key, modifiers);
    }

    /** Dieser Filter <b>oder</b> jener. */
    default KeyFilter or(KeyFilter other) {
        return (key, modifiers) -> routes(key, modifiers) || other.routes(key, modifiers);
    }

    /**
     * Diese Tasten zusätzlich.
     *
     * <p>Der häufigste Wunsch nach einer Gruppe: fast die richtige, nur noch
     * eine Taste dazu. {@code Keys.ARROWS.plus(GLFW_KEY_ENTER)} liest sich
     * besser als eine zweite Gruppe daneben.
     */
    default KeyFilter plus(int... keys) {
        return or(only(keys));
    }

    /**
     * Diese Tasten nicht.
     *
     * <p>Das Gegenstück: {@code KeyFilter.ALL.minus(GLFW_KEY_ESCAPE)} ist ein
     * Editor, aus dem der Spieler herauskommt.
     */
    default KeyFilter minus(int... keys) {
        return and(except(keys));
    }

    /** Genau das Gegenteil dieses Filters. */
    default KeyFilter negate() {
        return (key, modifiers) -> !routes(key, modifiers);
    }
}
