package dev.devpanda.bamboocef.web.runtime;

import java.awt.Cursor;

/**
 * Rückübersetzung der Zeigerkennung dieser Laufzeitumgebung.
 *
 * <p><b>Upstream java-cef meldet keinen CEF-Zeiger, sondern einen AWT-Zeiger.</b>
 * In {@code display_handler.cpp} steht ein {@code GetCursorId}, das
 * {@code cef_cursor_type_t} noch im nativen Teil auf die Konstanten von
 * {@link java.awt.Cursor} abbildet — {@code CT_HAND} wird zu
 * {@code HAND_CURSOR}, also aus 2 wird 12. Erst dieses Ergebnis erreicht
 * {@code onCursorChange}.
 *
 * <p>Der CinemaMod-Fork tat das nicht und reichte die Ordnungszahl von
 * Chromium durch; deshalb trägt {@code CursorType} genau diese Reihenfolge und
 * deshalb stimmt sie auf dem MCEF-Weg. Auf diesem Weg stimmt sie nicht — und
 * die Zahlen liegen so ungünstig, dass nichts abstürzt, sondern nur ein
 * falscher Zeiger erscheint: 12 ist dort der Größenänderungszeiger nach
 * Südwest, hier die Hand.
 *
 * <p><b>Was der native Teil wegwirft, bekommen wir nicht zurück.</b> Sein
 * {@code switch} kennt dreizehn Fälle und schickt alles übrige als
 * {@code DEFAULT_CURSOR}. Verbotsschild, Greifhand und Lupe kommen hier also
 * als Pfeil an. Für die Handprüfung ohne Belang — GLFW hat für Greifhand und
 * Lupe ohnehin keinen Zeiger —, und zurückholen ließe es sich nur mit einem
 * weiteren Patch am nativen Teil.
 */
final class AwtCursors {

    private AwtCursors() {
    }

    /**
     * Die Ordnungszahl in {@code cef_cursor_type_t} zu einer AWT-Kennung.
     *
     * <p>Die Rückgabe ist bewusst wieder eine nackte Zahl: Sie geht denselben
     * Weg wie auf dem MCEF-Pfad, und die Zuordnung zu einem GLFW-Zeiger bleibt
     * an der einen Stelle, an der sie schon steht.
     */
    static int toCefCursorType(int awtCursor) {
        return switch (awtCursor) {
            case Cursor.CROSSHAIR_CURSOR -> 1;   // CT_CROSS
            case Cursor.HAND_CURSOR -> 2;        // CT_HAND
            case Cursor.TEXT_CURSOR -> 3;        // CT_IBEAM
            case Cursor.WAIT_CURSOR -> 4;        // CT_WAIT
            case Cursor.E_RESIZE_CURSOR -> 6;    // CT_EASTRESIZE
            case Cursor.N_RESIZE_CURSOR -> 7;    // CT_NORTHRESIZE
            case Cursor.NE_RESIZE_CURSOR -> 8;   // CT_NORTHEASTRESIZE
            case Cursor.NW_RESIZE_CURSOR -> 9;   // CT_NORTHWESTRESIZE
            case Cursor.S_RESIZE_CURSOR -> 10;   // CT_SOUTHRESIZE
            case Cursor.SE_RESIZE_CURSOR -> 11;  // CT_SOUTHEASTRESIZE
            case Cursor.SW_RESIZE_CURSOR -> 12;  // CT_SOUTHWESTRESIZE
            case Cursor.W_RESIZE_CURSOR -> 13;   // CT_WESTRESIZE
            case Cursor.MOVE_CURSOR -> 29;       // CT_MOVE
            default -> 0;                        // CT_POINTER
        };
    }
}
