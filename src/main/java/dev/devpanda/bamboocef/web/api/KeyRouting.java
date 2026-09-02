package dev.devpanda.bamboocef.web.api;

import java.util.HashSet;
import java.util.Set;

/**
 * What belongs to a key press takes the same path as the press itself.
 *
 * <p><b>The filter is asked exactly once: on the press.</b> The character
 * GLFW delivers afterward, the repeat of a held key, and its release all
 * follow this one decision. Whoever asks the filter anew on every event gets
 * two bugs, both of which only show up in the game:
 *
 * <ul>
 *   <li>The game sees W pressed, the page sees W released — the player runs
 *       and never stops.</li>
 *   <li>The key went to the game, the character to the page — the text field
 *       holds a "w" that no one meant to type there.</li>
 * </ul>
 *
 * <p>Not thread-safe, and it need not be: keyboard events arrive on the
 * render thread, one after another.
 */
final class KeyRouting {

    private final KeyFilter filter;
    /** Keys whose press went to the surface and that are still held. */
    private final Set<Integer> routedDown = new HashSet<>();
    private boolean lastPressRouted;

    KeyRouting(KeyFilter filter) {
        this.filter = filter;
    }

    /**
     * Press or repeat.
     *
     * @return whether the surface gets the key
     */
    boolean press(int glfwKey, int modifiers) {
        // A held key stays with whoever got it on the press —
        // even if a modifier key has since joined.
        boolean routed = routedDown.contains(glfwKey) || filter.routes(glfwKey, modifiers);
        if (routed) {
            routedDown.add(glfwKey);
        }
        lastPressRouted = routed;
        return routed;
    }

    /**
     * Release.
     *
     * @return whether the surface gets the release — exactly when it got the
     *         press
     */
    boolean release(int glfwKey) {
        return routedDown.remove(glfwKey);
    }

    /** Does the surface get the character GLFW delivers after the last press? */
    boolean typed() {
        return lastPressRouted;
    }

    /** Forget everything — when focus leaves, nothing is held anymore. */
    void releaseAll() {
        routedDown.clear();
        lastPressRouted = false;
    }
}
