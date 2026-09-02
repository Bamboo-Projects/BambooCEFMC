package dev.devpanda.bamboocef.web.input;

import org.lwjgl.glfw.GLFW;

import java.awt.event.InputEvent;

/**
 * Modifier keys from GLFW to AWT.
 *
 * <p><b>The {@code _DOWN_MASK} family, not the old one.</b> AWT keeps two sets
 * of constants: {@code SHIFT_MASK} (1) from the early days and
 * {@code SHIFT_DOWN_MASK} (64) of today. JCEF's native side reads the new ones
 * — it calls {@code getModifiersEx}. The old ones look almost the same and
 * carry different numbers; whoever confuses them gets no error but keyboard
 * shortcuts that do not fire.
 *
 * <p><b>Two paths, one difference — and it is measured.</b> A key press carries
 * its modifiers along, a typed character does not. The reason is in
 * {@code tools/runtime/probe/KeyProbe.java}:
 *
 * <pre>
 *   KEY_TYPED '@' with Ctrl+Alt on the event   → nothing arrives
 *   KEY_TYPED '@' without modifiers             → '@' appears in the field
 * </pre>
 *
 * <p>Chromium reads a character with Ctrl as a control character and does not
 * insert it. On a German keyboard that is not an edge case: <b>Windows reports
 * AltGr as Ctrl + right Alt</b>, and so every {@code @}, {@code €}, {@code \},
 * {@code |} and {@code ~} would carry a Ctrl. Without this distinction a German
 * keyboard would be missing exactly the characters most needed in an editor.
 */
public final class AwtModifiers {

    /**
     * The modifiers for a key press or a release.
     *
     * <p>Complete — the keyboard shortcuts hang on it. Measured: Ctrl+A, C, V,
     * S and F arrive as {@code keydown} with {@code ctrlKey} and fire no
     * {@code keypress}, exactly as in a browser.
     */
    public static int forKey(int glfwModifiers) {
        int awt = 0;
        if ((glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            awt |= InputEvent.SHIFT_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            awt |= InputEvent.CTRL_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_ALT) != 0) {
            awt |= InputEvent.ALT_DOWN_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_SUPER) != 0) {
            awt |= InputEvent.META_DOWN_MASK;
        }
        return awt;
    }

    /**
     * The modifiers for a typed character.
     *
     * <p>Without Ctrl and without Alt. Shift stays: it no longer decides
     * anything — the character is already fixed — but it costs nothing and
     * keeps the event complete. Measured: with Shift on the {@code KEY_TYPED},
     * uppercase letters arrive unchanged.
     *
     * <p><b>Why it is not defused at the key press already.</b> A browser also
     * reports AltGr as Ctrl and Alt; passing the event on this way is therefore
     * not wrong but ordinary. Whether Monaco intercepts an AltGr combination as
     * a keyboard shortcut is decided by the hands-on check from B3e — not a
     * guess here. Should it come out negative, the countermeasure is sketched:
     * when Ctrl and right Alt are down together, drop both at the
     * {@code KEY_PRESSED} as well.
     */
    public static int forCharacter(int glfwModifiers) {
        return forKey(glfwModifiers) & ~(InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    }

    private AwtModifiers() {}
}
