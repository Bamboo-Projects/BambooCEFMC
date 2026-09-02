package dev.devpanda.bamboocef.web.view;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Sets the mouse cursor the page currently wants.
 *
 * <p><b>Why this lives here.</b> The table behind it was in the CinemaMod fork
 * of JCEF; upstream does not have it — there {@code onCursorChange} reports a
 * bare number. It therefore belongs to us and lives in {@link CursorType}.
 *
 * <p><b>And the number is not the same everywhere.</b> Upstream's native side
 * translates it to {@link java.awt.Cursor} first; that is converted back in
 * {@code AwtCursors} before it arrives here. What arrives here is always
 * Chromium's ordinal.
 *
 * <p>What remains is the keeping: {@code glfwCreateStandardCursor} creates a
 * new cursor every time, and one per mouse movement over a link would be a leak
 * that only shows up after hours.
 *
 * <p><b>What Chromium does not know stays the arrow.</b> Some of the cursors —
 * cell cross, progress, context menu — have no equivalent in GLFW and carry a
 * zero there. Forcing one of them would mean showing a wrong one; the arrow is
 * the more honest answer.
 */
public final class BrowserCursor {

    /** Created GLFW cursors, so that not every movement makes a new one. */
    private final Map<Integer, Long> handles = new HashMap<>();

    private int shownType = -1;

    /**
     * Shows the cursor for a Chromium identifier.
     *
     * <p>Must run on the render thread — GLFW does not tolerate window calls
     * from elsewhere.
     *
     * @param window     the GLFW window
     * @param cursorType the identifier as Chromium reports it
     */
    public void apply(long window, int cursorType) {
        if (cursorType == shownType) {
            return;
        }
        shownType = cursorType;
        CursorType wanted = CursorType.fromId(cursorType);
        if (wanted == CursorType.NONE) {
            // A page that hides the cursor means it — a video in fullscreen,
            // say, or a cursor of its own in the document.
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            return;
        }
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        long handle = handleFor(wanted);
        // Zero means, for GLFW: take the arrow. Exactly right for everything
        // that has no cursor of its own.
        GLFW.glfwSetCursor(window, handle);
    }

    private long handleFor(CursorType wanted) {
        if (wanted.glfwId == 0) {
            return 0L;
        }
        return handles.computeIfAbsent(wanted.glfwId, GLFW::glfwCreateStandardCursor);
    }

    /**
     * Restores the usual cursor.
     *
     * <p>Indispensable on close: a text cursor left standing over the game
     * world looks like a broken game.
     */
    public void reset(long window) {
        shownType = -1;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursor(window, 0L);
    }

    /** Releases the created cursors. */
    public void close(long window) {
        reset(window);
        for (long handle : handles.values()) {
            GLFW.glfwDestroyCursor(handle);
        }
        handles.clear();
    }
}
