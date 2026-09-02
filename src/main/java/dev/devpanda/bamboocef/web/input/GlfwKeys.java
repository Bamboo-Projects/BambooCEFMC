package dev.devpanda.bamboocef.web.input;

import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;

/**
 * GLFW-Tastencode nach AWT-Tastencode.
 *
 * <p><b>Auf Windows braucht diesen Wert niemand.</b> Der virtuelle Tastencode
 * entsteht dort aus Scancode und Erweiterungs-Bit — gemessen in
 * {@code tools/runtime/probe/KeyProbe.java}, und der Weg dorthin steht in
 * {@code sendKeyEventRaw}. Mitgeführt wird er trotzdem, weil die Zweige für
 * Linux und macOS ihn brauchen und weil ein Feld, das nur manchmal gefüllt
 * ist, später zur Fehlersuche wird.
 *
 * <p><b>Gerechnet wird nur, wo beide Zählungen denselben Ursprung haben.</b>
 * Buchstaben und Ziffern folgen in GLFW wie in AWT den ASCII-Werten; dort ist
 * die Gleichheit kein Zufall, sondern die Absicht beider. Alles andere steht
 * ausgeschrieben da. Eine Rechnung, die für neunzig Prozent stimmt, ist an
 * dieser Stelle schlimmer als eine Tabelle: Die fehlenden zehn Prozent fallen
 * erst auf, wenn jemand die Taste drückt.
 *
 * <p><b>Links und rechts fallen zusammen</b> — {@code VK_SHIFT} für beide
 * Umschalttasten, {@code VK_CONTROL} für beide Strg-Tasten. Das ist nicht
 * ungenau, sondern richtig: Genau diesen Wert stellt Windows in den
 * {@code wParam} einer echten Tastenmeldung, und die Seite steckt im
 * Erweiterungs-Bit, das {@link GlfwScancodes} mitführt.
 */
public final class GlfwKeys {

    /**
     * Der AWT-Tastencode zu einer GLFW-Taste.
     *
     * @return {@code KeyEvent.VK_*}, oder {@link KeyEvent#VK_UNDEFINED} für
     *         Tasten ohne Gegenstück. <b>Unbekannt heißt nicht: nicht
     *         schicken.</b> Auf Windows entsteht der virtuelle Tastencode
     *         ohnehin aus dem Scancode, und ein Ereignis zu verwerfen, nur
     *         weil dieses Feld leer bleibt, verlöre Tasten, die
     *         funktionieren würden.
     */
    public static int toAwtKeyCode(int glfwKey) {
        // Buchstaben und Ziffern: beide Zählungen folgen ASCII.
        if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) {
            return glfwKey;
        }
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) {
            return glfwKey;
        }

        return switch (glfwKey) {
            case GLFW.GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;

            // Steuertasten.
            case GLFW.GLFW_KEY_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;

            // Pfeile und Navigation.
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;

            // Funktionstasten. F25 hat kein Gegenstück in AWT.
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

            // Umschalttasten. Beide Seiten tragen denselben Wert; die Seite
            // steckt im Erweiterungs-Bit des Scancodes.
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> KeyEvent.VK_META;

            // Ziffernblock.
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

            // Umschaltzustände und Sondertasten.
            case GLFW.GLFW_KEY_CAPS_LOCK -> KeyEvent.VK_CAPS_LOCK;
            case GLFW.GLFW_KEY_NUM_LOCK -> KeyEvent.VK_NUM_LOCK;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> KeyEvent.VK_SCROLL_LOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> KeyEvent.VK_PRINTSCREEN;
            case GLFW.GLFW_KEY_PAUSE -> KeyEvent.VK_PAUSE;
            case GLFW.GLFW_KEY_MENU -> KeyEvent.VK_CONTEXT_MENU;

            // Absichtlich nicht dabei: die Zeichentasten des Layouts —
            // Komma, Punkt, Bindestrich, Klammern, Anführungszeichen und was
            // eine deutsche Tastatur sonst noch anders belegt. Was dort
            // herauskommt, weiß das Betriebssystem und niemand sonst; der Text
            // nimmt deshalb den Weg über charTyped.
            default -> KeyEvent.VK_UNDEFINED;
        };
    }

    private GlfwKeys() {}
}
