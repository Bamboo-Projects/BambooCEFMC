package dev.devpanda.bamboocef.web.capture;

/**
 * Ein fertiges Bild, so wie es an den Browser geht.
 *
 * <p><b>Unveränderlich, und das ist keine Stilfrage.</b> Chromium holt seine
 * Inhalte auf einem eigenen Thread ab, nicht auf dem, der zeichnet. Ein Puffer,
 * der sich unter dem Leser ändert, liefert halb altes und halb neues Bild — ein
 * Fehler, der nur manchmal auftritt und deshalb nie gefunden wird.
 *
 * <p>Der Ausweg ist der einfachste, den es gibt: Jedes Bild ist ein eigenes
 * Feld, das niemand mehr anfasst. Der Austausch ist dann eine einzige Zuweisung
 * und braucht keine Sperre.
 *
 * @param bytes      das kodierte Bild, wie es über die Leitung geht
 * @param mimeType   was Chromium daraus machen soll
 * @param width      Breite in Bildpunkten
 * @param height     Höhe in Bildpunkten
 * @param generation die wievielte Aufnahme — steht in der Adresse und macht
 *                   den Zwischenspeicher von Chromium wirkungslos
 */
public record CapturedFrame(byte[] bytes, String mimeType,
                            int width, int height, long generation) {

    public CapturedFrame {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Ein Bild ohne Inhalt ist keines");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Ein Bild ohne Maße ist keines");
        }
    }

    public int byteCount() {
        return bytes.length;
    }

    /** Wie viele Kilobyte, für die Ausgabe im Protokoll. */
    public double kilobytes() {
        return bytes.length / 1024.0;
    }
}
