package dev.devpanda.bamboocef.web.input;

/**
 * Mouse buttons from Minecraft to Chromium — and the note of what is pressed.
 *
 * <p><b>The numbers do not match, and that is not a quirk.</b> GLFW counts by
 * the order in which mice report their buttons:
 *
 * <pre>
 *   GLFW   0 = left    1 = right   2 = middle
 *   CEF    0 = left    1 = middle  2 = right
 * </pre>
 *
 * <p>Middle and right are swapped. Whoever overlooks this opens no context
 * menus with the right mouse button but pastes text instead — that is the
 * behavior of the middle button under X11 and looks like a bug in the page.
 *
 * <p>Checked in JCEF's native side: there {@code getButton()} is compared
 * against {@code GLFW_MOUSE_BUTTON_1/2/3} and turned into
 * {@code MBT_LEFT/MIDDLE/RIGHT}.
 *
 * <p><b>And why a note is necessary.</b> CEF carries pressed mouse buttons
 * along as extra flags — the same flags that also hold Ctrl and Shift. A mouse
 * movement without these flags looks to Chromium like a movement with the
 * button released, and every drag breaks off: text cannot be selected, a slider
 * jumps back.
 */
public final class MouseButtons {

    /** How Minecraft and GLFW count. */
    public static final int MINECRAFT_LEFT = 0;
    public static final int MINECRAFT_RIGHT = 1;
    public static final int MINECRAFT_MIDDLE = 2;

    /**
     * The flags for pressed buttons.
     *
     * <p>The values are hard-wired in JCEF's native side
     * ({@code modifiers & 0x10} and so on) and match
     * {@code CefMouseEvent.BUTTON1_MASK}. They are here once more so that this
     * class can be tested without Chromium on the classpath.
     */
    public static final int LEFT_MASK = 0x10;
    public static final int MIDDLE_MASK = 0x20;
    public static final int RIGHT_MASK = 0x40;

    private int pressedMask;

    /**
     * Translates a Minecraft button number into Chromium's numbering.
     *
     * @return the CEF number, or {@code -1} for a button Chromium does not know
     *         — the fourth and fifth mouse buttons are not part of the contract
     *         and would be discarded natively
     */
    public static int toBrowserButton(int minecraftButton) {
        return switch (minecraftButton) {
            case MINECRAFT_LEFT -> 0;
            case MINECRAFT_RIGHT -> 2;
            case MINECRAFT_MIDDLE -> 1;
            default -> -1;
        };
    }

    /** The flag for a Minecraft button number, or zero for unknown ones. */
    public static int maskOf(int minecraftButton) {
        return switch (minecraftButton) {
            case MINECRAFT_LEFT -> LEFT_MASK;
            case MINECRAFT_RIGHT -> RIGHT_MASK;
            case MINECRAFT_MIDDLE -> MIDDLE_MASK;
            default -> 0;
        };
    }

    /** Remembers that this button is now down. */
    public void press(int minecraftButton) {
        pressedMask |= maskOf(minecraftButton);
    }

    /** Remembers that this button is up again. */
    public void release(int minecraftButton) {
        pressedMask &= ~maskOf(minecraftButton);
    }

    /**
     * All pressed buttons as flags, together with the modifier keys.
     *
     * <p>Both must go into the same number: CEF reads mouse buttons and
     * modifier keys from the same field.
     */
    public int modifiersWith(int keyboardModifiers) {
        return pressedMask | keyboardModifiers;
    }

    /** Is any button down at all? */
    public boolean anyPressed() {
        return pressedMask != 0;
    }

    /**
     * Forgets all pressed buttons.
     *
     * <p>On leaving the surface and on close: a button that still counts as
     * pressed after release is stuck for Chromium in a drag operation for all
     * eternity.
     */
    public void forget() {
        pressedMask = 0;
    }
}
