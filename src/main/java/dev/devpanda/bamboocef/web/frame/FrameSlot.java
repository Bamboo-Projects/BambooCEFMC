package dev.devpanda.bamboocef.web.frame;

/**
 * Ein Postfach für genau ein Bild: das neueste gewinnt.
 *
 * <p><b>Keine Warteschlange.</b> Wenn der Browser schneller malt, als
 * Minecraft zeichnet, ist das vorletzte Bild wertlos — es zeigt eine
 * Vergangenheit, die niemand mehr sehen will. Eine wachsende Schlange
 * verwandelt diesen Vorsprung in Verzögerung: Der Spieler tippt, und der
 * Cursor kommt drei Bilder später. Deshalb verdrängt jedes neue Bild das
 * wartende.
 *
 * <p><b>Der Besitz wandert sichtbar.</b> {@link #offer} gibt zurück, was es
 * verdrängt hat, und {@link #poll} übergibt, was es herausnimmt. Nach beiden
 * Aufrufen hält das Postfach nichts mehr davon; wer ein Bild in der Hand hat,
 * schließt es oder verwendet es wieder. Das ist umständlicher als ein
 * stillschweigendes {@code close()} im Postfach und der einzige Weg, ein
 * Bilderrecycling später ohne Umbau anzuhängen — und ohne Recycling
 * alloziert man bei 1080p achteinhalb Megabyte je Bild.
 *
 * <p><b>Zur Nebenläufigkeit:</b> {@code onPaint} kommt heute im Render-Thread
 * an — dort pumpt {@code WebPump} Chromiums Nachrichtenschleife, einmal je
 * Bild. Ein Postfach über Threadgrenzen ist damit <i>nicht nötig</i>. Es steht
 * trotzdem hier, und zwar für die zwei Fälle, die absehbar kommen: ein eigener
 * Nachrichtentakt, sobald wir Chromium mit {@code external_begin_frame} an
 * unseren Renderloop hängen, und der GPU-Pfad, bei dem der Aufruf gerade nicht
 * dort ankommt, wo die Textur liegt. Die Synchronisierung kostet einen
 * unbestrittenen Monitor je Bild.
 */
public final class FrameSlot implements AutoCloseable {

    private BrowserFrame pending;
    private boolean closed;

    /** Wie viele Bilder verworfen wurden, weil das nächste schneller war. */
    private long dropped;

    /**
     * Legt ein Bild ab.
     *
     * @param frame das neue Bild, nicht {@code null}
     * @return das Bild, das dabei verdrängt wurde, oder {@code null}. <b>Der
     *         Aufrufer besitzt es danach</b> und muss es schließen oder
     *         wiederverwenden.
     */
    public synchronized BrowserFrame offer(BrowserFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Ein Bild ohne Bild");
        }
        if (closed) {
            // Nach dem Schließen nimmt das Postfach nichts mehr an. Das Bild
            // gehört weiterhin dem Aufrufer — hier still zu schließen wäre ein
            // Besitzwechsel, den niemand sieht.
            return frame;
        }
        BrowserFrame displaced = pending;
        pending = frame;
        if (displaced != null) {
            dropped++;
        }
        return displaced;
    }

    /**
     * Nimmt das wartende Bild heraus.
     *
     * @return das Bild, oder {@code null}, wenn seit dem letzten Abruf keines
     *         kam. <b>Der Aufrufer besitzt es danach.</b>
     */
    public synchronized BrowserFrame poll() {
        BrowserFrame found = pending;
        pending = null;
        return found;
    }

    /** Wartet gerade ein Bild? Nur für Anzeigen — es kann sofort veraltet sein. */
    public synchronized boolean hasFrame() {
        return pending != null;
    }

    /** Wie viele Bilder verdrängt wurden. Ein Maß dafür, wie weit der Browser vorausläuft. */
    public synchronized long dropped() {
        return dropped;
    }

    /**
     * Schließt das Postfach und das Bild darin.
     *
     * <p>Das wartende Bild hat niemand sonst in der Hand — es abzuräumen ist
     * hier kein versteckter Besitzwechsel, sondern der letzte, den es gibt.
     */
    @Override
    public synchronized void close() {
        closed = true;
        if (pending != null) {
            pending.close();
            pending = null;
        }
    }
}
