package dev.devpanda.bamboocef.web.api;

import org.lwjgl.glfw.GLFW;

/**
 * Ready-made key groups for {@link KeyFilter}.
 *
 * <p><b>Why they exist.</b> A quick menu needs "the arrow keys", a form
 * "everything that makes text", an overlay "everything except movement".
 * Without groups everyone rewrites the same list of numbers — and everyone
 * forgets a different key in it.
 *
 * <pre>
 * Keys.CLOSE.or(Keys.ARROWS)          a quick menu
 * Keys.TEXT_ENTRY                      an input field
 * KeyFilter.ALL.and(Keys.NOT_MOVEMENT) everything, but the player stays mobile
 * </pre>
 *
 * <p>The groups are filters, not lists: they can be combined with
 * {@link KeyFilter#and} and {@link KeyFilter#or} without anyone having to
 * concatenate arrays of numbers.
 */
public final class Keys {

    private Keys() {
    }

    /** Escape — the way out. Belongs in almost every overlay. */
    public static final KeyFilter CLOSE = KeyFilter.only(GLFW.GLFW_KEY_ESCAPE);

    /** The four arrow keys. */
    public static final KeyFilter ARROWS = KeyFilter.only(
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT);

    /** The Enter key and the one on the numpad. */
    public static final KeyFilter CONFIRM = KeyFilter.only(
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER);

    /**
     * What leads through a list: arrows, Page Up and Down, Home and End,
     * plus Tab.
     */
    public static final KeyFilter NAVIGATION = KeyFilter.only(
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN,
            GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END,
            GLFW.GLFW_KEY_TAB);

    /** What changes text without being a character itself: Backspace, Delete, Insert. */
    public static final KeyFilter EDITING = KeyFilter.only(
            GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_INSERT);

    /**
     * An input field: letters and numbers arrive as characters, here are the
     * keys around them.
     *
     * <p>Without {@link #CLOSE} — whoever builds a field decides for
     * themselves whether Escape closes it or discards the input.
     */
    public static final KeyFilter TEXT_ENTRY = NAVIGATION.or(EDITING).or(CONFIRM);

    /** The twelve function keys. */
    public static final KeyFilter FUNCTION = filterRange(
            GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_F12);

    /** The number keys of the top row — the game's hotbar. */
    public static final KeyFilter HOTBAR = filterRange(
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_9);

    /**
     * What a player moves with: W, A, S, D, Space, left Shift, left Ctrl.
     *
     * <p>Rarely meant as permission and almost always as the opposite —
     * that is what {@link #NOT_MOVEMENT} is for.
     */
    public static final KeyFilter MOVEMENT = KeyFilter.only(
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL);

    /**
     * Everything except movement.
     *
     * <p>The usual wish for an overlay that stays open a long time: the
     * surface may let you type, but the player should not get stuck.
     */
    public static final KeyFilter NOT_MOVEMENT = KeyFilter.except(
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL);

    /**
     * What forms a shortcut together with Ctrl: C, V, X, A, S, F, Z, Y.
     *
     * <p><b>Only with Ctrl held.</b> A filter that let a bare C through would
     * take the letter from the player — and the same key is something entirely
     * different without Ctrl.
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
     * An editor: everything a keyboard has to offer.
     *
     * <p>The same as {@link KeyFilter#ALL} and yet a name of its own — it says
     * why it is everything.
     */
    public static final KeyFilter EDITOR = KeyFilter.ALL;

    /** A contiguous range of key numbers, both ends included. */
    private static KeyFilter filterRange(int first, int last) {
        return (key, modifiers) -> key >= first && key <= last;
    }
}
