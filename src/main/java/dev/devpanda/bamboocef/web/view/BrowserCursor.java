package dev.devpanda.bamboocef.web.view;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Setzt den Mauszeiger, den die Seite gerade haben möchte.
 *
 * <p><b>Warum das hier steht.</b> Die Tabelle dahinter stand im
 * CinemaMod-Fork von JCEF; upstream hat sie nicht — dort meldet
 * {@code onCursorChange} eine nackte Zahl. Sie gehört deshalb uns und steht in
 * {@link CursorType}.
 *
 * <p><b>Und die Zahl ist nicht überall dieselbe.</b> Upstreams nativer Teil
 * übersetzt vorher nach {@link java.awt.Cursor}; zurückgerechnet wird das in
 * {@code AwtCursors}, bevor es hier ankommt. Was hier eintrifft, ist immer die
 * Ordnungszahl von Chromium.
 *
 * <p>Übrig bleibt das Aufbewahren: {@code glfwCreateStandardCursor} legt jedes
 * Mal einen neuen Zeiger an, und einer je Mausbewegung über einen Link wäre
 * ein Leck, das man erst nach Stunden bemerkt.
 *
 * <p><b>Was Chromium nicht kennt, bleibt der Pfeil.</b> Ein Teil der Zeiger —
 * Zellenkreuz, Fortschritt, Kontextmenü — hat in GLFW keine Entsprechung und
 * trägt dort eine Null. Einen davon zu erzwingen hieße, einen falschen zu
 * zeigen; der Pfeil ist die ehrlichere Antwort.
 */
public final class BrowserCursor {

    /** Angelegte GLFW-Zeiger, damit nicht jede Bewegung einen neuen erzeugt. */
    private final Map<Integer, Long> handles = new HashMap<>();

    private int shownType = -1;

    /**
     * Zeigt den Zeiger zu einer Chromium-Kennung.
     *
     * <p>Muss im Render-Thread laufen — GLFW verträgt keine Fensteraufrufe von
     * anderswo.
     *
     * @param window     das Fenster von GLFW
     * @param cursorType die Kennung, wie Chromium sie meldet
     */
    public void apply(long window, int cursorType) {
        if (cursorType == shownType) {
            return;
        }
        shownType = cursorType;
        CursorType wanted = CursorType.fromId(cursorType);
        if (wanted == CursorType.NONE) {
            // Eine Seite, die den Zeiger versteckt, meint das ernst — etwa ein
            // Video im Vollbild oder ein eigener Zeiger im Dokument.
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            return;
        }
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        long handle = handleFor(wanted);
        // Null heißt bei GLFW: nimm den Pfeil. Genau richtig für alles, wofür
        // es keinen eigenen Zeiger gibt.
        GLFW.glfwSetCursor(window, handle);
    }

    private long handleFor(CursorType wanted) {
        if (wanted.glfwId == 0) {
            return 0L;
        }
        return handles.computeIfAbsent(wanted.glfwId, GLFW::glfwCreateStandardCursor);
    }

    /**
     * Gibt den gewohnten Zeiger zurück.
     *
     * <p>Beim Schließen unerlässlich: Ein Textcursor, der über der Spielwelt
     * stehen bleibt, sieht nach einem kaputten Spiel aus.
     */
    public void reset(long window) {
        shownType = -1;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursor(window, 0L);
    }

    /** Gibt die angelegten Zeiger frei. */
    public void close(long window) {
        reset(window);
        for (long handle : handles.values()) {
            GLFW.glfwDestroyCursor(handle);
        }
        handles.clear();
    }
}
