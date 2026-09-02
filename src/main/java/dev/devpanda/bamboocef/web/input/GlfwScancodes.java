package dev.devpanda.bamboocef.web.input;

/**
 * The scancode from GLFW, split into what Windows expects.
 *
 * <p><b>The one number carries two pieces of information.</b> Windows does not
 * distinguish arrow keys and numpad through the scancode — both share the lower
 * eight bits — but through an extension bit that appears in real key messages
 * as the prefix {@code 0xE0} and in the {@code lParam} as bit 24. GLFW packs
 * this marker into bit 8 instead.
 *
 * <p><b>Measured, not derived</b> ({@code tools/runtime/probe/ScanProbe.java},
 * August 31, 2026):
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
 * <p><b>What happens when the bit is lost</b> is likewise measured
 * ({@code tools/runtime/probe/KeyProbe.java}): arrow-up would arrive as
 * {@code keyCode 104} — the eight of the numpad. The editor would write a digit
 * instead of moving the cursor.
 *
 * <p>This class is deliberately platform-dependent and therefore separate from
 * {@link GlfwKeys}: the decomposition holds for Windows. Linux and macOS bring
 * their own scancodes and are not version 1.
 */
public final class GlfwScancodes {

    /** The bit in which GLFW records "extended key" on Windows. */
    private static final int EXTENDED_FLAG = 0x100;

    /** The lower eight bits — the scancode Windows expects in the lParam. */
    public static int base(int glfwScancode) {
        return glfwScancode & 0xFF;
    }

    /**
     * Whether Windows records the key as extended.
     *
     * <p>True for arrows, Insert, Delete, Home, End, Page Up and Page Down,
     * right Ctrl, right Alt and the numpad Enter key.
     */
    public static boolean extended(int glfwScancode) {
        return (glfwScancode & EXTENDED_FLAG) != 0;
    }

    private GlfwScancodes() {}
}
