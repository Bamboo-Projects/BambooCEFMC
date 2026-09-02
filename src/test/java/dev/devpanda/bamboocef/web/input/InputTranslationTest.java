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
 * Die Übersetzung zwischen Minecrafts Eingaben und dem, was Chromium erwartet.
 *
 * <p>Die Fehler, die diese Prüfläufe verhindern sollen, sehen im Spiel nicht
 * nach Übersetzungsfehlern aus: Die Pfeiltaste schreibt eine Ziffer, das
 * {@code @} fehlt auf einer deutschen Tastatur, die rechte Maustaste fügt Text
 * ein statt ein Menü zu öffnen, und das Rad scrollt verkehrt herum.
 *
 * <p><b>Die Erwartungen sind nicht ausgedacht.</b> Sie stammen aus zwei
 * Messungen ohne Minecraft: {@code tools/runtime/probe/ScanProbe.java} für die
 * Scancodes und {@code tools/runtime/probe/KeyProbe.java} für das, was
 * Chromium von einem Tastendruck wirklich sieht.
 */
class InputTranslationTest {

    // ---- GlfwScancodes ------------------------------------------------------

    @Test
    @DisplayName("Pfeiltaste und Ziffernblock teilen den Scancode und trennt das Bit")
    void extendedFlagSeparatesArrowsFromNumpad() {
        // Gemessen am 31. August 2026 mit ScanProbe.
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
    @DisplayName("Rechte Umschalttasten sind erweitert, linke nicht")
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
    @DisplayName("Buchstaben und Ziffern gehen direkt, weil beide Zählungen ASCII folgen")
    void lettersAndDigitsMapDirectly() {
        assertEquals(KeyEvent.VK_A, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_A));
        assertEquals(KeyEvent.VK_Z, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_Z));
        assertEquals(KeyEvent.VK_0, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_0));
        assertEquals(KeyEvent.VK_9, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_9));
    }

    @Test
    @DisplayName("Die Tabelle deckt Steuerung, Navigation, Ziffernblock und Funktionstasten ab")
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
    @DisplayName("Links und rechts tragen denselben Tastencode — die Seite steckt im Scancode")
    void bothSidesShareTheKeyCode() {
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT));
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_CONTROL));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_CONTROL));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_LEFT_ALT));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_RIGHT_ALT));
    }

    @Test
    @DisplayName("Layout-Tasten liefern VK_UNDEFINED — und werden trotzdem geschickt")
    void layoutKeysAreUndefinedOnPurpose() {
        // Was auf einer deutschen Tastatur an dieser Stelle steht, weiß nur
        // das Betriebssystem. Der Text nimmt den Weg über charTyped.
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_SEMICOLON));
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_GRAVE_ACCENT));
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKeyCode(GLFW.GLFW_KEY_WORLD_1));

        // Auf Windows ist der Wert ohne Bedeutung: Der virtuelle Tastencode
        // entsteht dort aus dem Scancode. Ein Ereignis zu verwerfen, weil
        // dieses Feld leer bleibt, verlöre Tasten, die funktionieren.
        assertEquals(0, KeyEvent.VK_UNDEFINED,
                "VK_UNDEFINED ist null — nichts, worauf ein Aufrufer prüfen müsste");
    }

    // ---- AwtModifiers -------------------------------------------------------

    @Test
    @DisplayName("Die DOWN_MASK-Familie, nicht die alte")
    void modifiersUseTheDownMaskFamily() {
        assertEquals(InputEvent.SHIFT_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_SHIFT));
        assertEquals(InputEvent.CTRL_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_CONTROL));
        assertEquals(InputEvent.ALT_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_ALT));
        assertEquals(InputEvent.META_DOWN_MASK, AwtModifiers.forKey(GLFW.GLFW_MOD_SUPER));

        assertEquals(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                AwtModifiers.forKey(GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
    }

    @Test
    @DisplayName("Am getippten Zeichen fallen Strg und Alt weg — sonst fehlt das AltGr-Zeichen")
    void charactersDropCtrlAndAlt() {
        // Gemessen: KEY_TYPED '@' mit Strg+Alt kommt nicht an, ohne beide
        // schon. Windows meldet AltGr als Strg + rechtes Alt.
        int altGr = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;
        assertEquals(0, AwtModifiers.forCharacter(altGr));

        // Umschalt bleibt: Es entscheidet nichts mehr, kostet nichts und hält
        // das Ereignis vollständig.
        assertEquals(InputEvent.SHIFT_DOWN_MASK,
                AwtModifiers.forCharacter(GLFW.GLFW_MOD_SHIFT | altGr));
    }

    // ---- AwtMouseEvents -----------------------------------------------------

    @Test
    @DisplayName("Rechts ist BUTTON3, nicht BUTTON2")
    void rightIsButtonThree() {
        assertEquals(MouseEvent.BUTTON1, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_LEFT));
        assertEquals(MouseEvent.BUTTON3, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_RIGHT),
                "sonst öffnet die rechte Taste kein Menü, sondern fügt Text ein");
        assertEquals(MouseEvent.BUTTON2, AwtMouseEvents.toAwtButton(MouseButtons.MINECRAFT_MIDDLE));
    }

    @Test
    @DisplayName("Bewegung mit gedrückter Taste ist ein Ziehen, sonst eine Bewegung")
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
    @DisplayName("Der Klickzähler geht als clickCount hinaus")
    void clickCountTravels() {
        MouseEvent doubleClick = AwtMouseEvents.button(5, 6, true,
                MouseButtons.MINECRAFT_LEFT, 2, InputEvent.BUTTON1_DOWN_MASK);

        assertEquals(MouseEvent.MOUSE_PRESSED, doubleClick.getID());
        assertEquals(2, doubleClick.getClickCount(),
                "daran und nur daran erkennt Chromium einen Doppelklick");
        assertEquals(MouseEvent.BUTTON1, doubleClick.getButton());
    }

    @Test
    @DisplayName("Das Rad reicht Minecrafts Vorzeichen unverändert weiter")
    void wheelKeepsTheSign() {
        // Gemessen mit KeyProbe: wheelRotation +1 erzeugt in der Seite
        // deltaY -2.0, also hinauf. Minecrafts Delta ist nach oben positiv —
        // also wandert es unverändert hinein.
        MouseWheelEvent up = AwtMouseEvents.wheel(0, 0, 1, 3, 0);
        MouseWheelEvent down = AwtMouseEvents.wheel(0, 0, -1, 3, 0);

        assertEquals(1, up.getWheelRotation());
        assertEquals(-1, down.getWheelRotation());
        assertEquals(MouseWheelEvent.WHEEL_UNIT_SCROLL, up.getScrollType());
        assertEquals(3, up.getScrollAmount(),
                "der Wert überschreibt nativ das Delta");
    }

    @Test
    @DisplayName("Der Absender ist peerlos und wird nie gezeigt")
    void eventSourceIsPeerless() {
        assertNotNull(AwtEventSource.SOURCE, "null wirft in AWTs Konstruktoren");
        assertEquals(null, AwtEventSource.SOURCE.getParent(),
                "sonst liefe CefClient.onTakeFocus in eine Fokuswanderung");
        assertTrue(!AwtEventSource.SOURCE.isDisplayable(),
                "kein Peer, kein Toolkit, kein Fenster");
    }
}
