import org.lwjgl.glfw.GLFW;

/**
 * Was GLFW unter Windows als Scancode meldet — gemessen, nicht vermutet.
 *
 * <p><b>Wozu.</b> Der Windows-Zweig von java-cef berechnet den virtuellen
 * Tastencode aus dem Scancode. Ob GLFW ihn in der Form liefert, die Windows
 * dafür erwartet, entscheidet darüber, ob Pfeiltasten als Pfeiltasten oder als
 * Ziffernblock ankommen — beide teilen sich denselben Basis-Scancode.
 *
 * <p><b>Das Ergebnis vom 31. August 2026:</b> GLFW setzt für erweiterte Tasten
 * Bit 8 (0x100), Windows erwartet stattdessen ein Präfix 0xE0. Die Umrechnung
 * gehört deshalb auf die Java-Seite, nach {@code GlfwScancodes}:
 *
 * <pre>
 *   UP      0x0148 = 0x100 | 0x48        KP_8   0x0048
 *   DOWN    0x0150 = 0x100 | 0x50        KP_2   0x0050
 *   RCTRL   0x011D = 0x100 | 0x1D        LCTRL  0x001D
 * </pre>
 *
 * <p>Aufruf (Klassenpfad: lwjgl und lwjgl-glfw samt Natives):
 * <pre>java -cp "&lt;lwjgl&gt;;&lt;glfw&gt;;." ScanProbe</pre>
 */
public class ScanProbe {
    record Taste(String name, int glfw) {}

    public static void main(String[] args) {
        if (!GLFW.glfwInit()) {
            System.out.println("glfwInit fehlgeschlagen");
            return;
        }
        Taste[] tasten = {
            new Taste("A",              GLFW.GLFW_KEY_A),
            new Taste("1",              GLFW.GLFW_KEY_1),
            new Taste("ENTER",          GLFW.GLFW_KEY_ENTER),
            new Taste("BACKSPACE",      GLFW.GLFW_KEY_BACKSPACE),
            new Taste("ESCAPE",         GLFW.GLFW_KEY_ESCAPE),
            new Taste("TAB",            GLFW.GLFW_KEY_TAB),
            new Taste("LEFT",           GLFW.GLFW_KEY_LEFT),
            new Taste("RIGHT",          GLFW.GLFW_KEY_RIGHT),
            new Taste("UP",             GLFW.GLFW_KEY_UP),
            new Taste("DOWN",           GLFW.GLFW_KEY_DOWN),
            new Taste("INSERT",         GLFW.GLFW_KEY_INSERT),
            new Taste("DELETE",         GLFW.GLFW_KEY_DELETE),
            new Taste("HOME",           GLFW.GLFW_KEY_HOME),
            new Taste("END",            GLFW.GLFW_KEY_END),
            new Taste("PAGE_UP",        GLFW.GLFW_KEY_PAGE_UP),
            new Taste("PAGE_DOWN",      GLFW.GLFW_KEY_PAGE_DOWN),
            new Taste("LEFT_CONTROL",   GLFW.GLFW_KEY_LEFT_CONTROL),
            new Taste("RIGHT_CONTROL",  GLFW.GLFW_KEY_RIGHT_CONTROL),
            new Taste("LEFT_ALT",       GLFW.GLFW_KEY_LEFT_ALT),
            new Taste("RIGHT_ALT",      GLFW.GLFW_KEY_RIGHT_ALT),
            new Taste("LEFT_SHIFT",     GLFW.GLFW_KEY_LEFT_SHIFT),
            new Taste("RIGHT_SHIFT",    GLFW.GLFW_KEY_RIGHT_SHIFT),
            new Taste("KP_ENTER",       GLFW.GLFW_KEY_KP_ENTER),
            new Taste("KP_DIVIDE",      GLFW.GLFW_KEY_KP_DIVIDE),
            new Taste("KP_MULTIPLY",    GLFW.GLFW_KEY_KP_MULTIPLY),
            new Taste("KP_8",           GLFW.GLFW_KEY_KP_8),
            new Taste("KP_2",           GLFW.GLFW_KEY_KP_2),
            new Taste("KP_0",           GLFW.GLFW_KEY_KP_0),
            new Taste("PRINT_SCREEN",   GLFW.GLFW_KEY_PRINT_SCREEN),
            new Taste("PAUSE",          GLFW.GLFW_KEY_PAUSE),
            new Taste("F1",             GLFW.GLFW_KEY_F1),
            new Taste("F12",            GLFW.GLFW_KEY_F12),
            new Taste("SPACE",          GLFW.GLFW_KEY_SPACE),
        };
        System.out.printf("%-16s %6s %10s %8s%n", "Taste", "GLFW", "Scancode", "hex");
        for (Taste t : tasten) {
            int sc = GLFW.glfwGetKeyScancode(t.glfw());
            System.out.printf("%-16s %6d %10d   0x%04X%n", t.name(), t.glfw(), sc, sc);
        }
        GLFW.glfwTerminate();
    }
}
