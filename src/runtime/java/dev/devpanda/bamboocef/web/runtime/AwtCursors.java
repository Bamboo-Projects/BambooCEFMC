package dev.devpanda.bamboocef.web.runtime;

import java.awt.Cursor;

/**
 * Reverse-translation of this runtime's cursor identifier.
 *
 * <p><b>Upstream java-cef does not report a CEF cursor, but an AWT cursor.</b>
 * In {@code display_handler.cpp} there is a {@code GetCursorId} that, still in
 * the native part, maps {@code cef_cursor_type_t} onto the constants of
 * {@link java.awt.Cursor} — {@code CT_HAND} becomes {@code HAND_CURSOR}, so 2
 * turns into 12. Only this result reaches {@code onCursorChange}.
 *
 * <p>The CinemaMod fork did not do that and passed Chromium's ordinal through;
 * that is why {@code CursorType} carries exactly this ordering, and why it is
 * correct on the MCEF path. On this path it is not correct — and the numbers
 * happen to line up so awkwardly that nothing crashes, only a wrong cursor
 * appears: 12 is the south-west resize cursor there, the hand here.
 *
 * <p><b>What the native part throws away, we do not get back.</b> Its
 * {@code switch} knows thirteen cases and sends everything else on as
 * {@code DEFAULT_CURSOR}. So the not-allowed sign, the grab cursor and the
 * zoom cursor arrive here as an arrow. Irrelevant for the hand check — GLFW has
 * no cursor for grab and zoom anyway — and recovering them would only be
 * possible with a further patch to the native part.
 */
final class AwtCursors {

    private AwtCursors() {
    }

    /**
     * The ordinal in {@code cef_cursor_type_t} for a given AWT identifier.
     *
     * <p>The return value is deliberately a bare number again: it takes the
     * same path as on the MCEF route, and the mapping to a GLFW cursor stays in
     * the one place where it already lives.
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
