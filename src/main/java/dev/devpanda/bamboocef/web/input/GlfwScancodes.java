package dev.devpanda.bamboocef.web.input;

/**
 * Der Scancode von GLFW, zerlegt in das, was Windows erwartet.
 *
 * <p><b>Die eine Zahl trägt zwei Angaben.</b> Windows unterscheidet
 * Pfeiltasten und Ziffernblock nicht über den Scancode — beide teilen sich die
 * unteren acht Bit — sondern über ein Erweiterungs-Bit, das in echten
 * Tastenmeldungen als Präfix {@code 0xE0} und im {@code lParam} als Bit 24
 * steht. GLFW packt dieses Kennzeichen stattdessen in Bit 8.
 *
 * <p><b>Gemessen, nicht hergeleitet</b> ({@code tools/runtime/probe/ScanProbe.java},
 * 31. August 2026):
 *
 * <pre>
 *   UP       0x0148 = 0x100 | 0x48        KP_8     0x0048
 *   DOWN     0x0150 = 0x100 | 0x50        KP_2     0x0050
 *   INSERT   0x0152 = 0x100 | 0x52        KP_0     0x0052
 *   RCTRL    0x011D = 0x100 | 0x1D        LCTRL    0x001D
 *   RALT     0x0138 = 0x100 | 0x38        LALT     0x0038
 *   KP_ENTER 0x011C = 0x100 | 0x1C        ENTER    0x001C
 * </pre>
 *
 * <p><b>Was passiert, wenn das Bit verlorengeht,</b> ist ebenfalls gemessen
 * ({@code tools/runtime/probe/KeyProbe.java}): Pfeil-hoch käme als
 * {@code keyCode 104} an — die Acht des Ziffernblocks. Der Editor schriebe
 * eine Ziffer, statt den Cursor zu bewegen.
 *
 * <p>Diese Klasse ist bewusst plattformabhängig und deshalb von
 * {@link GlfwKeys} getrennt: Die Zerlegung gilt für Windows. Linux und macOS
 * bringen eigene Scancodes mit und sind nicht Version 1.
 */
public final class GlfwScancodes {

    /** Das Bit, in dem GLFW unter Windows „erweiterte Taste" führt. */
    private static final int EXTENDED_FLAG = 0x100;

    /** Die unteren acht Bit — der Scancode, den Windows im lParam erwartet. */
    public static int base(int glfwScancode) {
        return glfwScancode & 0xFF;
    }

    /**
     * Ob Windows die Taste als erweitert führt.
     *
     * <p>Wahr für Pfeile, Einfügen, Entfernen, Pos1, Ende, Bild auf und ab,
     * rechtes Strg, rechtes Alt und die Eingabetaste des Ziffernblocks.
     */
    public static boolean extended(int glfwScancode) {
        return (glfwScancode & EXTENDED_FLAG) != 0;
    }

    private GlfwScancodes() {}
}
