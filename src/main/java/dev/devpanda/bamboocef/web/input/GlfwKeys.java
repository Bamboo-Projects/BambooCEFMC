package dev.devpanda.bamboocef.web.input;

import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;

/**
 * GLFW key code to AWT key code.
 *
 * <p><b>On Windows nobody needs this value.</b> There the virtual key code is
 * formed from scancode and extension bit — measured in
 * {@code tools/runtime/probe/KeyProbe.java}, and the path to it is in
 * {@code sendKeyEventRaw}. It is carried along anyway, because the branches for
 * Linux and macOS need it and because a field that is only sometimes filled
 * becomes a source of bugs later.
 *
 * <p><b>Computation happens only where both numberings share the same
 * origin.</b> Letters and digits follow the ASCII values in GLFW as in AWT;
 * there the equality is no accident but the intent of both. Everything else is
 * spelled out. A computation that is right for ninety percent is worse than a
 * table at this spot: the missing ten percent only show up when someone presses
 * the key.
 *
 * <p><b>Left and right collapse into one</b> — {@code VK_SHIFT} for both Shift
 * keys, {@code VK_CONTROL} for both Ctrl keys. That is not imprecise but
 * correct: this is exactly the value Windows puts into the {@code wParam} of a
 * real key message, and the side sits in the extension bit that
 * {@link GlfwScancodes} carries along.
 */
public final class GlfwKeys {

    /**
     * The AWT key code for a GLFW key.
     *
     * @return {@code KeyEvent.VK_*}, or {@link KeyEvent#VK_UNDEFINED} for keys
     *         with no counterpart. <b>Unknown does not mean: do not send.</b>
     *         On Windows the virtual key code is formed from the scancode
     *         anyway, and discarding an event just because this field stays
     *         empty would lose keys that would work.
     */
    public static int toAwtKeyCode(int glfwKey) {
        // Letters and digits: both numberings follow ASCII.
        if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) {
            return glfwKey;
        }
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) {
            return glfwKey;
        }

        return switch (glfwKey) {
            case GLFW.GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;

            // Control keys.
            case GLFW.GLFW_KEY_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;

            // Arrows and navigation.
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;

            // Function keys. F25 has no counterpart in AWT.
            case GLFW.GLFW_KEY_F1 -> KeyEvent.VK_F1;
            case GLFW.GLFW_KEY_F2 -> KeyEvent.VK_F2;
            case GLFW.GLFW_KEY_F3 -> KeyEvent.VK_F3;
            case GLFW.GLFW_KEY_F4 -> KeyEvent.VK_F4;
            case GLFW.GLFW_KEY_F5 -> KeyEvent.VK_F5;
            case GLFW.GLFW_KEY_F6 -> KeyEvent.VK_F6;
            case GLFW.GLFW_KEY_F7 -> KeyEvent.VK_F7;
            case GLFW.GLFW_KEY_F8 -> KeyEvent.VK_F8;
            case GLFW.GLFW_KEY_F9 -> KeyEvent.VK_F9;
            case GLFW.GLFW_KEY_F10 -> KeyEvent.VK_F10;
            case GLFW.GLFW_KEY_F11 -> KeyEvent.VK_F11;
            case GLFW.GLFW_KEY_F12 -> KeyEvent.VK_F12;
            case GLFW.GLFW_KEY_F13 -> KeyEvent.VK_F13;
            case GLFW.GLFW_KEY_F14 -> KeyEvent.VK_F14;
            case GLFW.GLFW_KEY_F15 -> KeyEvent.VK_F15;
            case GLFW.GLFW_KEY_F16 -> KeyEvent.VK_F16;
            case GLFW.GLFW_KEY_F17 -> KeyEvent.VK_F17;
            case GLFW.GLFW_KEY_F18 -> KeyEvent.VK_F18;
            case GLFW.GLFW_KEY_F19 -> KeyEvent.VK_F19;
            case GLFW.GLFW_KEY_F20 -> KeyEvent.VK_F20;
            case GLFW.GLFW_KEY_F21 -> KeyEvent.VK_F21;
            case GLFW.GLFW_KEY_F22 -> KeyEvent.VK_F22;
            case GLFW.GLFW_KEY_F23 -> KeyEvent.VK_F23;
            case GLFW.GLFW_KEY_F24 -> KeyEvent.VK_F24;

            // Modifier keys. Both sides carry the same value; the side
            // sits in the extension bit of the scancode.
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> KeyEvent.VK_META;

            // Numpad.
            case GLFW.GLFW_KEY_KP_0 -> KeyEvent.VK_NUMPAD0;
            case GLFW.GLFW_KEY_KP_1 -> KeyEvent.VK_NUMPAD1;
            case GLFW.GLFW_KEY_KP_2 -> KeyEvent.VK_NUMPAD2;
            case GLFW.GLFW_KEY_KP_3 -> KeyEvent.VK_NUMPAD3;
            case GLFW.GLFW_KEY_KP_4 -> KeyEvent.VK_NUMPAD4;
            case GLFW.GLFW_KEY_KP_5 -> KeyEvent.VK_NUMPAD5;
            case GLFW.GLFW_KEY_KP_6 -> KeyEvent.VK_NUMPAD6;
            case GLFW.GLFW_KEY_KP_7 -> KeyEvent.VK_NUMPAD7;
            case GLFW.GLFW_KEY_KP_8 -> KeyEvent.VK_NUMPAD8;
            case GLFW.GLFW_KEY_KP_9 -> KeyEvent.VK_NUMPAD9;
            case GLFW.GLFW_KEY_KP_DECIMAL -> KeyEvent.VK_DECIMAL;
            case GLFW.GLFW_KEY_KP_DIVIDE -> KeyEvent.VK_DIVIDE;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> KeyEvent.VK_MULTIPLY;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> KeyEvent.VK_SUBTRACT;
            case GLFW.GLFW_KEY_KP_ADD -> KeyEvent.VK_ADD;
            case GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_KP_EQUAL -> KeyEvent.VK_EQUALS;

            // Lock states and special keys.
            case GLFW.GLFW_KEY_CAPS_LOCK -> KeyEvent.VK_CAPS_LOCK;
            case GLFW.GLFW_KEY_NUM_LOCK -> KeyEvent.VK_NUM_LOCK;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> KeyEvent.VK_SCROLL_LOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> KeyEvent.VK_PRINTSCREEN;
            case GLFW.GLFW_KEY_PAUSE -> KeyEvent.VK_PAUSE;
            case GLFW.GLFW_KEY_MENU -> KeyEvent.VK_CONTEXT_MENU;

            // Deliberately not included: the character keys of the layout —
            // comma, period, hyphen, brackets, quotation marks and whatever
            // else a German keyboard maps differently. What comes out there is
            // known to the operating system and nobody else; the text
            // therefore takes the path through charTyped.
            default -> KeyEvent.VK_UNDEFINED;
        };
    }

    private GlfwKeys() {}
}
