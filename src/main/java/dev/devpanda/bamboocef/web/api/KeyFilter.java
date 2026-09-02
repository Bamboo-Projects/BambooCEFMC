package dev.devpanda.bamboocef.web.api;

import java.util.Arrays;

/**
 * Which keys a surface gets — and which the game keeps.
 *
 * <p><b>The reason this exists.</b> An editor needs every key; a quick menu
 * needs Escape and the arrow keys, and W, A, S, and D belong to the player.
 * A surface that can only get all or nothing is useless for the second case —
 * and that one is the more common.
 *
 * <p><b>The decision is made before forwarding, not after.</b> Whoever first
 * sends a key to Chromium and then hopes that Minecraft still sees it too gets
 * both halfway: the page reacts, and the player runs off anyway.
 *
 * <p>The key numbers are GLFW's, the same ones Minecraft uses —
 * {@code GLFW_KEY_ESCAPE} and relatives. Deliberately no enumeration of its
 * own: whoever builds in a surface has the numbers in front of them anyway.
 */
@FunctionalInterface
public interface KeyFilter {

    /**
     * Does the surface get this key?
     *
     * @param glfwKey   the key per GLFW
     * @param modifiers the modifier keys held, likewise per GLFW
     */
    boolean routes(int glfwKey, int modifiers);

    /** Everything goes to the surface — what an editor needs. */
    KeyFilter ALL = (key, modifiers) -> true;

    /** Nothing goes to the surface. It is then a picture to look at. */
    KeyFilter NONE = (key, modifiers) -> false;

    /**
     * Only these keys, regardless of modifier keys.
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
     * Everything except these keys.
     *
     * <p>For the case where a surface wants almost everything: an editor the
     * player should still be able to escape with Escape is
     * {@code KeyFilter.except(GLFW_KEY_ESCAPE)}.
     */
    static KeyFilter except(int... keys) {
        int[] blocked = keys.clone();
        Arrays.sort(blocked);
        return (key, modifiers) -> Arrays.binarySearch(blocked, key) < 0;
    }

    /** This filter <b>and</b> that one — a key must pass through both. */
    default KeyFilter and(KeyFilter other) {
        return (key, modifiers) -> routes(key, modifiers) && other.routes(key, modifiers);
    }

    /** This filter <b>or</b> that one. */
    default KeyFilter or(KeyFilter other) {
        return (key, modifiers) -> routes(key, modifiers) || other.routes(key, modifiers);
    }

    /**
     * These keys in addition.
     *
     * <p>The most common wish after a group: almost the right one, just one
     * more key on top. {@code Keys.ARROWS.plus(GLFW_KEY_ENTER)} reads better
     * than a second group beside it.
     */
    default KeyFilter plus(int... keys) {
        return or(only(keys));
    }

    /**
     * Not these keys.
     *
     * <p>The counterpart: {@code KeyFilter.ALL.minus(GLFW_KEY_ESCAPE)} is an
     * editor the player can get out of.
     */
    default KeyFilter minus(int... keys) {
        return and(except(keys));
    }

    /** Exactly the opposite of this filter. */
    default KeyFilter negate() {
        return (key, modifiers) -> !routes(key, modifiers);
    }
}
