package dev.devpanda.bamboocef.web.view;

/**
 * Welcher Mauszeiger Chromiums Kennung entspricht.
 *
 * <p><b>Warum diese Tabelle uns gehört.</b> Sie stand als Aufzählungstyp im
 * CinemaMod-Fork von JCEF und war der Grund, warum {@code BrowserCursor} sie
 * nicht selbst führen musste. Upstream java-cef hat sie nicht — dort meldet
 * {@code onCursorChange} eine nackte Zahl und sonst nichts. Die Abbildung
 * gehört also hierher, und dann gehört sie hierher für beide Wege.
 *
 * <p><b>Die Reihenfolge ist der Vertrag.</b> Chromium meldet den Zeiger als
 * Ordnungszahl seiner eigenen Aufzählung {@code cef_cursor_type_t}; welcher
 * Name an welcher Stelle steht, ist die ganze Information. Umsortieren heißt
 * hier: alles falsch zuordnen.
 *
 * <p><b>Was GLFW nicht kennt, bleibt der Pfeil.</b> Zellenkreuz, Fortschritt,
 * Kontextmenü und die Schwenkzeiger haben dort keine Entsprechung und tragen
 * eine Null. Einen davon zu erzwingen hieße, einen falschen zu zeigen.
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

    /** Die Kennung von GLFW, oder null für „dafür gibt es keinen". */
    public final int glfwId;

    CursorType(int glfwId) {
        this.glfwId = glfwId;
    }

    /**
     * Der Zeiger zu einer Kennung von Chromium.
     *
     * <p><b>Unbekannt wird zum Pfeil, nicht zum Absturz.</b> Die Vorlage im
     * Fork griff mit {@code values()[id]} zu und flog bei jeder Zahl, die eine
     * neuere Chromium-Fassung hinzufügt, aus dem Rahmen. Ein Zeiger ist nichts,
     * wofür ein Spiel stehenbleiben sollte.
     */
    public static CursorType fromId(int id) {
        CursorType[] all = values();
        return id >= 0 && id < all.length ? all[id] : POINTER;
    }
}
