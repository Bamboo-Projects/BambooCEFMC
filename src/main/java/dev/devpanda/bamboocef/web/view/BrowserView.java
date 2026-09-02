package dev.devpanda.bamboocef.web.view;

/**
 * Wo ein Browser liegt, wie groß er ist — und die eine Umrechnung dazwischen.
 *
 * <p><b>Drei Einheiten, die niemand verwechseln darf.</b>
 *
 * <table border="1">
 *   <caption>Die Einheiten und wo sie gelten</caption>
 *   <tr><th>Einheit</th><th>Woher</th><th>Wofür</th></tr>
 *   <tr>
 *     <td><b>Framebuffer-Pixel</b></td>
 *     <td>{@code window.getWidth()}</td>
 *     <td>Was die Grafikkarte wirklich hat. Bei einem 4K-Schirm viermal so
 *         viele wie bei Full HD.</td>
 *   </tr>
 *   <tr>
 *     <td><b>GUI-Einheiten</b></td>
 *     <td>{@code screen.width}, Mauskoordinaten</td>
 *     <td>Minecrafts eigenes Raster. Bei GUI-Skalierung 3 ist eine
 *         GUI-Einheit drei Framebuffer-Pixel breit.</td>
 *   </tr>
 *   <tr>
 *     <td><b>Browser-Pixel</b></td>
 *     <td>{@code browser.resize(w, h)}</td>
 *     <td>Wie groß Chromium seine Seite malt. Hier gleich CSS-Pixeln, weil
 *         diese JCEF-Fassung keinen {@code device_scale_factor} setzt.</td>
 *   </tr>
 * </table>
 *
 * <p><b>Die Entscheidung: ein Browser-Pixel ist ein Framebuffer-Pixel.</b> Der
 * Browser bekommt so viele Pixel, wie der Bereich auf dem Schirm wirklich
 * einnimmt — nicht so viele, wie Minecrafts Raster ihm zugesteht. Sonst wäre
 * Schrift bei GUI-Skalierung 3 auf ein Drittel der Auflösung gerechnet und
 * beim Zeichnen wieder aufgeblasen. Für einen Editor ist das der Unterschied
 * zwischen lesbar und matschig.
 *
 * <p><b>Und warum das hier steht und nicht zweimal woanders.</b> Zeichnen und
 * Maus müssen dieselbe Rechnung benutzen, sonst klickt der Spieler neben das,
 * was er sieht — ein Fehler, der sich als „die Seite reagiert falsch" tarnt
 * und tagelang gesucht wird. Später soll ein Strahl auf eine Fläche in der
 * Welt genauso rechnen: Er liefert einen Anteil von 0 bis 1 und geht über
 * {@link #fromFraction(double, double)} in dieselbe Ebene.
 *
 * <p>Reine Rechnung, kein Minecraft. Die Skalierung kommt als Zahl herein,
 * damit sie sich prüfen lässt.
 *
 * @param guiX      linke Kante in GUI-Einheiten
 * @param guiY      obere Kante in GUI-Einheiten
 * @param guiWidth  Breite in GUI-Einheiten
 * @param guiHeight Höhe in GUI-Einheiten
 * @param guiScale  wie viele Framebuffer-Pixel eine GUI-Einheit misst
 */
public record BrowserView(int guiX, int guiY, int guiWidth, int guiHeight, double guiScale) {

    public BrowserView {
        if (guiWidth <= 0 || guiHeight <= 0) {
            throw new IllegalArgumentException("Eine Fläche ohne Ausdehnung zeigt nichts");
        }
        if (guiScale <= 0.0) {
            throw new IllegalArgumentException("Eine Skalierung von null macht jede Umrechnung sinnlos");
        }
    }

    /** Die ganze Oberfläche, ohne Rand. */
    public static BrowserView fullscreen(int guiWidth, int guiHeight, double guiScale) {
        return new BrowserView(0, 0, guiWidth, guiHeight, guiScale);
    }

    /**
     * Wie breit der Browser malen soll, in Browser-Pixeln.
     *
     * <p>Mindestens eins: Chromium mit Breite null malt nie wieder, und ein
     * Fenster, das gerade zugezogen wird, geht durch diesen Zustand.
     */
    public int browserWidth() {
        return Math.max(1, (int) Math.round(guiWidth * guiScale));
    }

    public int browserHeight() {
        return Math.max(1, (int) Math.round(guiHeight * guiScale));
    }

    /** Liegt dieser Punkt in GUI-Einheiten über der Fläche? */
    public boolean contains(double guiMouseX, double guiMouseY) {
        return guiMouseX >= guiX && guiMouseX < guiX + guiWidth
                && guiMouseY >= guiY && guiMouseY < guiY + guiHeight;
    }

    /**
     * Ein Mauspunkt in GUI-Einheiten wird zu einem Browser-Pixel.
     *
     * <p>Geklemmt auf die Fläche: Chromium mit einer Koordinate außerhalb
     * seines Fensters zu füttern, hebt Ziehvorgänge auf, die noch laufen. Wer
     * wissen will, ob der Zeiger überhaupt drin ist, fragt {@link #contains}.
     */
    public int toBrowserX(double guiMouseX) {
        return clamp((guiMouseX - guiX) * guiScale, browserWidth());
    }

    public int toBrowserY(double guiMouseY) {
        return clamp((guiMouseY - guiY) * guiScale, browserHeight());
    }

    /**
     * Ein Anteil von 0 bis 1 wird zu einem Browser-Pixel.
     *
     * <p>Der Weg für eine Fläche in der Welt: Ein Strahl trifft sie irgendwo,
     * und wo genau, sagt sich am ehesten als Anteil. Damit landet auch ein
     * Bildschirm an der Wand in derselben Ebene wie dieser Screen — und in
     * derselben Rechnung.
     *
     * @return Browser-Pixel als {@code [x, y]}
     */
    public int[] fromFraction(double acrossFraction, double downFraction) {
        return new int[] {
                clamp(acrossFraction * browserWidth(), browserWidth()),
                clamp(downFraction * browserHeight(), browserHeight())
        };
    }

    /**
     * Ein Browser-Pixel wird zu einem Punkt in GUI-Einheiten.
     *
     * <p>Für alles, was Chromium in seinen eigenen Koordinaten meldet und was
     * an die richtige Stelle auf dem Schirm muss — ein aufgeklapptes
     * Auswahlfeld zum Beispiel.
     *
     * @return GUI-Einheiten als {@code [x, y]}
     */
    public double[] toGui(int browserX, int browserY) {
        return new double[] {guiX + browserX / guiScale, guiY + browserY / guiScale};
    }

    /** Ein Maß in Browser-Pixeln wird zu einem Maß in GUI-Einheiten. */
    public double lengthToGui(int browserLength) {
        return browserLength / guiScale;
    }

    /** Dieselbe Fläche mit anderer Größe — beim Ziehen am Fensterrand. */
    public BrowserView resized(int newGuiWidth, int newGuiHeight, double newGuiScale) {
        return new BrowserView(guiX, guiY, newGuiWidth, newGuiHeight, newGuiScale);
    }

    /** Ob eine Meldung an Chromium überhaupt nötig ist. */
    public boolean sameSizeAs(BrowserView other) {
        return other != null
                && browserWidth() == other.browserWidth()
                && browserHeight() == other.browserHeight();
    }

    private static int clamp(double value, int limit) {
        int rounded = (int) Math.floor(value);
        return Math.max(0, Math.min(limit - 1, rounded));
    }
}
