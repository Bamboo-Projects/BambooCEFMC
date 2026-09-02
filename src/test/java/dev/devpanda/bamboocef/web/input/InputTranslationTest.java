package dev.devpanda.bamboocef.web.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The translation between Minecraft's inputs and what Chromium expects.
 *
 * <p>The faults these test runs are meant to prevent do not look like
 * translation errors in the game: the arrow key writes a digit, the
 * {@code @} is missing on a German keyboard, the right mouse button inserts text
 * instead of opening a menu, and the wheel scrolls the wrong way round.
 *
 * <p><b>The expectations are not made up.</b> They come from two
 * measurements without Minecraft: {@code tools/runtime/probe/ScanProbe.java} for the
 * scancodes and {@code tools/runtime/probe/KeyProbe.java} for what
 * Chromium really sees of a key press.
 */
class InputTranslationTest {

    // ---- GlfwScancodes ------------------------------------------------------

    @Test
    @DisplayName("Arrow key and numpad share the scancode and the bit separates them")
    void extendedFlagSeparatesArrowsFromNumpad() {
        // Measured on 31 August 2026 with ScanProbe.
        int up = 0x0148;
        int numpad8 = 0x0048;

        assertEquals(0x48, GlfwScancodes.base(up));
        assertEquals(0x48, GlfwScancodes.base(numpad8),
                "beide teilen die unteren acht Bit — daran ändert sich nichts");
        assertTrue(GlfwScancodes.extended(up),
                "ohne dieses Bit schriebe die Pfeiltaste eine Acht");
        assertTrue(!GlfwScancodes.extended(numpad8));
    }

    @Test
    @DisplayName("Right modifier keys are extended, left ones not")
    void extendedFlagSeparatesLeftFromRight() {
        assertTrue(GlfwScancodes.extended(0x011D), "rechtes Strg");
        assertTrue(!GlfwScancodes.extended(0x001D), "linkes Strg");
        assertTrue(GlfwScancodes.extended(0x0138), "rechtes Alt");
        assertTrue(!GlfwScancodes.extended(0x0038), "linkes Alt");
        assertTrue(GlfwScancodes.extended(0x011C), "Eingabe des Ziffernblocks");
        assertTrue(!GlfwScancodes.extended(0x001C), "Eingabe");
    }

    // ---- GlfwKeys -----------------------------------------------------------

    @Test
    @DisplayName("Letters and digits map directly, because both numberings follow ASCII")
    void lettersAndDigitsMapDirectly() {
        assertEquals(KeyEvent.VK_A, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_A));
        assertEquals(KeyEvent.VK_Z, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_Z));
        assertEquals(KeyEvent.VK_0, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_0));
        assertEquals(KeyEvent.VK_9, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_9));
    }

    @Test
    @DisplayName("The table covers control, navigation, numpad and function keys")
    void tableCoversTheKeysThatMatter() {
        assertEquals(KeyEvent.VK_ENTER, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_ENTER));
        assertEquals(KeyEvent.VK_ESCAPE, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_ESCAPE));
        assertEquals(KeyEvent.VK_TAB, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_TAB));
        assertEquals(KeyEvent.VK_BACK_SPACE, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_BACKSPACE));
        assertEquals(KeyEvent.VK_DELETE, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_DELETE));
        assertEquals(KeyEvent.VK_INSERT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_INSERT));

        assertEquals(KeyEvent.VK_UP, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_UP));
        assertEquals(KeyEvent.VK_DOWN, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_DOWN));
        assertEquals(KeyEvent.VK_LEFT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT));
        assertEquals(KeyEvent.VK_RIGHT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT));
        assertEquals(KeyEvent.VK_HOME, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_HOME));
        assertEquals(KeyEvent.VK_END, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_END));
        assertEquals(KeyEvent.VK_PAGE_UP, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_PAGE_UP));
        assertEquals(KeyEvent.VK_PAGE_DOWN, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_PAGE_DOWN));

        assertEquals(KeyEvent.VK_NUMPAD0, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_KP_0));
        assertEquals(KeyEvent.VK_NUMPAD9, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_KP_9));
        assertEquals(KeyEvent.VK_ENTER, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_KP_ENTER));
        assertEquals(KeyEvent.VK_DIVIDE, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_KP_DIVIDE));

        assertEquals(KeyEvent.VK_F1, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_F1));
        assertEquals(KeyEvent.VK_F12, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_F12));
        assertEquals(KeyEvent.VK_F24, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_F24));
    }

    @Test
    @DisplayName("Left and right carry the same key code — the side sits in the scancode")
    void bothSidesShareTheKeyCode() {
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT));
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_CONTROL));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_CONTROL));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_ALT));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_ALT));
    }

    @Test
    @DisplayName("Layout keys yield VK_UNDEFINED — and are sent anyway")
    void layoutKeysAreUndefinedOnPurpose() {
        // What sits at this spot on a German keyboard only the
        // operating system knows. The text takes the path via charTyped.
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_SEMICOLON));
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_GRAVE_ACCENT));
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_WORLD_1));

        // On Windows the value has no meaning: the virtual key code
        // arises there from the scancode. Discarding an event because
        // this field stays empty would lose keys that work.
        assertEquals(0, KeyEvent.VK_UNDEFINED,
                "VK_UNDEFINED ist null — nichts, worauf ein Aufrufer prüfen müsste");
    }

    // ---- AwtModifiers -------------------------------------------------------

    @Test
    @DisplayName("The DOWN_MASK family, not the old one")
    void modifiersUseTheDownMaskFamily() {
        assertEquals(InputEvent.SHIFT_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_SHIFT));
        assertEquals(InputEvent.CTRL_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_CONTROL));
        assertEquals(InputEvent.ALT_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_ALT));
        assertEquals(InputEvent.META_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_SUPER));

        assertEquals(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                AwtModifiers.forKey(GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
    }

    @Test
    @DisplayName("On the typed character Ctrl and Alt drop away — otherwise the AltGr character is missing")
    void charactersDropCtrlAndAlt() {
        // Measured: KEY_TYPED '@' with Ctrl+Alt does not arrive, without both
        // it does. Windows reports AltGr as Ctrl + right Alt.
        int altGr = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;
        assertEquals(0, AwtModifiers.forCharacter(altGr));

        // Shift stays: it decides nothing anymore, costs nothing and keeps
        // the event complete.
        assertEquals(InputEvent.SHIFT_DOWN_MASK,
                AwtModifiers.forCharacter(GLFW.GLFW_MOD_SHIFT | altGr));
    }

    // ---- AwtMouseEvents -----------------------------------------------------

    @Test
    @DisplayName("Right is BUTTON3, not BUTTON2")
    void rightIsButtonThree() {
        assertEquals(MouseEvent.BUTTON1, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_LEFT));
        assertEquals(MouseEvent.BUTTON3, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_RIGHT),
                "sonst öffnet die rechte Taste kein Menü, sondern fügt Text ein");
        assertEquals(MouseEvent.BUTTON2, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_MIDDLE));
    }

    @Test
    @DisplayName("Movement with a pressed button is a drag, otherwise a move")
    void draggingIsItsOwnEvent() {
        MouseEvent moving = AwtMouseEvents.moved(10, 20, 0, false);
        MouseEvent dragging = AwtMouseEvents.moved(10, 20,
                InputEvent.BUTTON1_DOWN_MASK, true);

        assertEquals(MouseEvent.MOUSE_MOVED, moving.getID());
        assertEquals(MouseEvent.MOUSE_DRAGGED, dragging.getID(),
                "ohne das entsteht beim Ziehen keine Textauswahl");
        assertEquals(10, dragging.getX());
        assertEquals(20, dragging.getY());
    }

    @Test
    @DisplayName("The click counter goes out as clickCount")
    void clickCountTravels() {
        MouseEvent doubleClick = AwtMouseEvents.button(5, 6, true,
                MouseButtons.MINECRAFT_LEFT, 2, InputEvent.BUTTON1_DOWN_MASK);

        assertEquals(MouseEvent.MOUSE_PRESSED, doubleClick.getID());
        assertEquals(2, doubleClick.getClickCount(),
                "daran und nur daran erkennt Chromium einen Doppelklick");
        assertEquals(MouseEvent.BUTTON1, doubleClick.getButton());
    }

    @Test
    @DisplayName("The wheel passes Minecraft's sign along unchanged")
    void wheelKeepsTheSign() {
        // Measured with KeyProbe: wheelRotation +1 produces deltaY -2.0 in the
        // page, that is upward. Minecraft's delta is positive upward —
        // so it passes in unchanged.
        MouseWheelEvent up = AwtMouseEvents.wheel(0, 0, 1, 3, 0);
        MouseWheelEvent down = AwtMouseEvents.wheel(0, 0, -1, 3, 0);

        assertEquals(1, up.getWheelRotation());
        assertEquals(-1, down.getWheelRotation());
        assertEquals(MouseWheelEvent.WHEEL_UNIT_SCROLL, up.getScrollType());
        assertEquals(3, up.getScrollAmount(),
                "der Wert überschreibt nativ das Delta");
    }

    @Test
    @DisplayName("The sender is peerless and is never shown")
    void eventSourceIsPeerless() {
        assertNotNull(AwtEventSource.SOURCE, "null wirft in AWTs Konstruktoren");
        assertEquals(null, AwtEventSource.SOURCE.getParent(),
                "sonst liefe CefClient.onTakeFocus in eine Fokuswanderung");
        assertTrue(!AwtEventSource.SOURCE.isDisplayable(),
                "kein Peer, kein Toolkit, kein Fenster");
    }
}
