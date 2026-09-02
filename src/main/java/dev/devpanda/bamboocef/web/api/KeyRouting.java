package dev.devpanda.bamboocef.web.api;

import java.util.HashSet;
import java.util.Set;

/**
 * Was zu einem Tastendruck gehört, geht denselben Weg wie er.
 *
 * <p><b>Der Filter wird genau einmal gefragt: beim Drücken.</b> Das Zeichen,
 * das GLFW danach liefert, die Wiederholung einer gehaltenen Taste und ihr
 * Loslassen folgen dieser einen Entscheidung. Wer den Filter bei jedem
 * Ereignis neu fragt, bekommt zwei Fehler, die beide erst im Spiel auffallen:
 *
 * <ul>
 *   <li>Das Spiel sieht W gedrückt, die Seite sieht W losgelassen — der
 *       Spieler läuft und hört nicht mehr auf.</li>
 *   <li>Die Taste ging ans Spiel, das Zeichen an die Seite — im Textfeld
 *       steht ein „w“, das niemand dort tippen wollte.</li>
 * </ul>
 *
 * <p>Nicht threadsicher und muss es nicht sein: Tastatur-Ereignisse kommen
 * im Renderthread an, eines nach dem anderen.
 */
final class KeyRouting {

    private final KeyFilter filter;
    /** Tasten, deren Drücken an die Fläche ging und die noch gehalten sind. */
    private final Set<Integer> routedDown = new HashSet<>();
    private boolean lastPressRouted;

    KeyRouting(KeyFilter filter) {
        this.filter = filter;
    }

    /**
     * Drücken oder Wiederholen.
     *
     * @return ob die Fläche die Taste bekommt
     */
    boolean press(int glfwKey, int modifiers) {
        // Eine gehaltene Taste bleibt bei dem, der sie beim Drücken bekam —
        // auch wenn inzwischen eine Umschalttaste dazukam.
        boolean routed = routedDown.contains(glfwKey) || filter.routes(glfwKey, modifiers);
        if (routed) {
            routedDown.add(glfwKey);
        }
        lastPressRouted = routed;
        return routed;
    }

    /**
     * Loslassen.
     *
     * @return ob die Fläche das Loslassen bekommt — genau dann, wenn sie das
     *         Drücken bekam
     */
    boolean release(int glfwKey) {
        return routedDown.remove(glfwKey);
    }

    /** Bekommt die Fläche das Zeichen, das GLFW nach dem letzten Drücken liefert? */
    boolean typed() {
        return lastPressRouted;
    }

    /** Alles vergessen — wenn der Fokus geht, ist nichts mehr gehalten. */
    void releaseAll() {
        routedDown.clear();
        lastPressRouted = false;
    }
}
