package dev.devpanda.bamboocef.web.input;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Mouse events for Chromium, built from what Minecraft reports.
 *
 * <p><b>Why the mouse stays with AWT but the keyboard does not.</b> For the
 * mouse the native side reads only public methods — {@code getID},
 * {@code getX}, {@code getY}, {@code getClickCount}, {@code getButton},
 * {@code getModifiersEx}, and for the wheel additionally {@code getScrollType},
 * {@code getWheelRotation} and {@code getUnitsToScroll}. All of it can be set
 * through the constructors. For the keyboard that is precisely not so: there
 * everything hangs on a private field that only the native AWT code fills.
 *
 * <p><b>No {@code MOUSE_CLICKED}.</b> Chromium builds the click from down and
 * up. An extra event would be a second click and would distort double and
 * triple clicks.
 */
public final class AwtMouseEvents {

    /**
     * Minecraft's button number in AWT's numbering.
     *
     * <p>Middle and right are swapped — GLFW counts in the order in which mice
     * report their buttons, AWT by meaning. The same trap as in
     * {@link MouseButtons}, only with different target values.
     */
    public static int toAwtButton(int minecraftButton) {
        return switch (minecraftButton) {
            case MouseButtons.MINECRAFT_LEFT -> MouseEvent.BUTTON1;
            case MouseButtons.MINECRAFT_RIGHT -> MouseEvent.BUTTON3;
            case MouseButtons.MINECRAFT_MIDDLE -> MouseEvent.BUTTON2;
            default -> MouseEvent.NOBUTTON;
        };
    }

    /**
     * The "this button is down" flag for {@code getModifiersEx}.
     *
     * <p>CEF reads pressed mouse buttons from the same field as Ctrl and Shift.
     * If the flag is missing, a movement looks to Chromium like one with the
     * button released, and every drag breaks off.
     */
    public static int buttonDownMask(int minecraftButton) {
        return switch (minecraftButton) {
            case MouseButtons.MINECRAFT_LEFT -> InputEvent.BUTTON1_DOWN_MASK;
            case MouseButtons.MINECRAFT_RIGHT -> InputEvent.BUTTON3_DOWN_MASK;
            case MouseButtons.MINECRAFT_MIDDLE -> InputEvent.BUTTON2_DOWN_MASK;
            default -> 0;
        };
    }

    /**
     * The combined mask, from Minecraft's convention into AWT's family.
     *
     * <p>What comes in carries two things in one number: GLFW's modifiers in
     * the lower four bits and the pressed mouse buttons in {@code 0x10},
     * {@code 0x20} and {@code 0x40} — the flags that {@link MouseButtons} keeps
     * for Chromium's native side.
     *
     * <p><b>The two sets overlap, and that is not an oversight one could fix
     * here.</b> GLFW puts Caps Lock on {@code 0x10} and Num Lock on
     * {@code 0x20} — the same bits as the left and middle mouse buttons. They
     * stay separate only because GLFW does not report these two states at all
     * without {@code GLFW_LOCK_KEY_MODS}. That is why only the lower four bits
     * are read as modifiers here: what lies above belongs to the mouse.
     */
    public static int toAwtModifiers(int combinedModifiers) {
        int awt = AwtModifiers.forKey(combinedModifiers & 0x0F);
        if ((combinedModifiers & MouseButtons.LEFT_MASK) != 0) {
            awt |= InputEvent.BUTTON1_DOWN_MASK;
        }
        if ((combinedModifiers & MouseButtons.MIDDLE_MASK) != 0) {
            awt |= InputEvent.BUTTON2_DOWN_MASK;
        }
        if ((combinedModifiers & MouseButtons.RIGHT_MASK) != 0) {
            awt |= InputEvent.BUTTON3_DOWN_MASK;
        }
        return awt;
    }

    /** Whether the combined mask contains any mouse button at all. */
    public static boolean anyButtonDown(int combinedModifiers) {
        return (combinedModifiers
                & (MouseButtons.LEFT_MASK | MouseButtons.MIDDLE_MASK | MouseButtons.RIGHT_MASK))
                != 0;
    }

    /**
     * Chromium's button number in AWT's numbering.
     *
     * <p>The session has already translated via
     * {@link MouseButtons#toBrowserButton}; here it only goes one numbering
     * further.
     */
    public static int browserButtonToAwt(int cefButton) {
        return switch (cefButton) {
            case 0 -> MouseEvent.BUTTON1;
            case 1 -> MouseEvent.BUTTON2;
            case 2 -> MouseEvent.BUTTON3;
            default -> MouseEvent.NOBUTTON;
        };
    }

    /**
     * A movement.
     *
     * @param dragging whether a button is down during it — Chromium
     *                 distinguishes moving and dragging, and a text selection
     *                 arises only while dragging
     */
    public static MouseEvent moved(int x, int y, int awtModifiers, boolean dragging) {
        return new MouseEvent(AwtEventSource.SOURCE,
                dragging ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false, MouseEvent.NOBUTTON);
    }

    /**
     * The pointer leaves the surface.
     *
     * <p>Without this event Chromium does not undo hover states, and the last
     * button touched stays highlighted forever.
     */
    public static MouseEvent exited(int x, int y, int awtModifiers) {
        return new MouseEvent(AwtEventSource.SOURCE, MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false, MouseEvent.NOBUTTON);
    }

    /**
     * A button goes down or up.
     *
     * @param clickCount 1, 2 or 3 — by this and only this does Chromium
     *                   recognize a double click. The value comes from
     *                   {@link ClickCounter}.
     */
    public static MouseEvent button(int x, int y, boolean down, int minecraftButton,
            int clickCount, int awtModifiers) {
        return awtButton(x, y, down, toAwtButton(minecraftButton), clickCount, awtModifiers);
    }

    /** The same, when the button number is already AWT's. */
    public static MouseEvent awtButton(int x, int y, boolean down, int awtButton, int clickCount,
            int awtModifiers) {
        return new MouseEvent(AwtEventSource.SOURCE,
                down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), awtModifiers, x, y, Math.max(1, clickCount), false,
                awtButton);
    }

    /**
     * A detent of the wheel.
     *
     * <p><b>The sign passes through unchanged — measured, not guessed.</b>
     * Three counting conventions meet here: GLFW reports a positive delta for
     * up, AWT a negative {@code wheelRotation} for up, and Chromium's
     * {@code deltaY} is likewise negative for up. Two sign flips that cancel
     * each other out — but that is a claim until someone checks.
     * {@code tools/runtime/probe/KeyProbe.java} checked:
     *
     * <pre>
     *   wheelRotation +1  →  deltaY -2.0   in the page: up
     *   wheelRotation -1  →  deltaY +2.0   in the page: down
     * </pre>
     *
     * <p>Minecraft's delta is positive when scrolling up. It therefore moves
     * into {@code wheelRotation} unchanged.
     *
     * @param notches      Minecraft's detents, positive for up
     * @param unitsPerNotch lines per detent. The value goes into
     *                      {@code UnitsToScroll} and overrides the delta there;
     *                      three is the value MCEF used.
     */
    public static MouseWheelEvent wheel(int x, int y, int notches, int unitsPerNotch,
            int awtModifiers) {
        return new MouseWheelEvent(AwtEventSource.SOURCE, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, unitsPerNotch, notches);
    }

    private AwtMouseEvents() {}
}
