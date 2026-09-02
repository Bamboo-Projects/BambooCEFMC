package dev.devpanda.bamboocef.web.view;

/**
 * Which mouse cursor corresponds to Chromium's identifier.
 *
 * <p><b>Why this table belongs to us.</b> It was an enum type in the CinemaMod
 * fork of JCEF and was the reason {@code BrowserCursor} did not have to keep it
 * itself. Upstream java-cef does not have it — there {@code onCursorChange}
 * reports a bare number and nothing else. The mapping therefore belongs here,
 * and then it belongs here for both paths.
 *
 * <p><b>The order is the contract.</b> Chromium reports the cursor as the
 * ordinal of its own enum {@code cef_cursor_type_t}; which name stands at which
 * position is the whole of the information. Reordering here means: mapping
 * everything wrong.
 *
 * <p><b>What GLFW does not know stays the arrow.</b> Cell cross, progress,
 * context menu and the panning cursors have no equivalent there and carry a
 * zero. Forcing one of them would mean showing a wrong one.
 */
public enum CursorType {
    POINTER(0),
    CROSS(0x36003),
    HAND(0x36004),
    IBEAM(0x36002),
    WAIT(0),
    HELP(0),
    EAST_RESIZE(0x36005),
    NORTH_RESIZE(0x36006),
    NORTH_EAST_RESIZE(0x36008),
    NORTH_WEST_RESIZE(0x36007),
    SOUTH_RESIZE(0x36006),
    SOUTH_EAST_RESIZE(0x36007),
    SOUTH_WEST_RESIZE(0x36008),
    WEST_RESIZE(0x36005),
    NORTH_SOUTH_RESIZE(0x36006),
    EAST_WEST_RESIZE(0x36005),
    NORTH_EAST_SOUTH_WEST_RESIZE(0x36008),
    NORTH_WEST_SOUTH_EAST_RESIZE(0x36007),
    COLUMN_RESIZE(0),
    ROW_RESIZE(0),
    MIDDLE_PANNING(0),
    EAST_PANNING(0),
    NORTH_PANNING(0),
    NORTH_EAST_PANNING(0),
    NORTH_WEST_PANNING(0),
    SOUTH_PANNING(0),
    SOUTH_EAST_PANNING(0),
    SOUTH_WEST_PANNING(0),
    WEST_PANNING(0),
    MOVE(0x36009),
    VERTICAL_IBEAM(0),
    CELL(0),
    CONTEXT_MENU(0),
    ALIAS(0),
    PROGRESS(0),
    NO_DROP(0x3600A),
    COPY(0),
    NONE(0),
    NOT_ALLOWED(0x3600A),
    ZOOM_IN(0),
    ZOOM_OUT(0),
    GRAB(0),
    GRABBING(0),
    CUSTOM(0);

    /** The GLFW identifier, or zero for "there is none for it". */
    public final int glfwId;

    CursorType(int glfwId) {
        this.glfwId = glfwId;
    }

    /**
     * The cursor for an identifier from Chromium.
     *
     * <p><b>Unknown becomes the arrow, not a crash.</b> The template in the
     * fork accessed it with {@code values()[id]} and blew up on every number a
     * newer Chromium version adds. A cursor is nothing a game should halt for.
     */
    public static CursorType fromId(int id) {
        CursorType[] all = values();
        return id >= 0 && id < all.length ? all[id] : POINTER;
    }
}
