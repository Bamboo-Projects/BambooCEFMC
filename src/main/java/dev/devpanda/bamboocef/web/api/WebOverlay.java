package dev.devpanda.bamboocef.web.api;

/**
 * Eine Fläche über dem Bild, in Bildschirmpunkten.
 *
 * <p><b>Bildschirmpunkte, nicht Pixel.</b> Ort und Größe sind die Einheiten,
 * in denen Minecraft seine Oberfläche zeichnet — dieselben, die der Spieler
 * mit der GUI-Skalierung wählt. Die Fläche rechnet selbst in Browserpixel
 * um und schärft nach, wenn sich die Skalierung ändert. Wer in Pixeln denkt,
 * bekommt bei jedem Skalierungswechsel eine andere Größe.
 *
 * <p>Gezeichnet wird über dem Spiel, unter jedem offenen Bildschirm, in der
 * Reihenfolge des Öffnens. F1 blendet die Fläche mit dem Rest der
 * Oberfläche aus.
 *
 * <p>Eingaben hängen am {@link OverlayFocus}: Ohne Fokus ist die Fläche ein
 * Bild. Mit Tastaturfokus bekommt sie, was ihr {@link KeyFilter} nimmt, und
 * der Spieler behält Maus und Rest. Mit Mausfokus ist der Zeiger frei.
 * <b>F10 beendet jeden Fokus</b>, gleich, was der Filter sagt — sonst gäbe
 * es eine Fläche, aus der niemand herauskommt.
 *
 * <p>Fassung 1. Was hier steht, bleibt.
 */
public interface WebOverlay extends AutoCloseable {

    /** Die Fläche darunter: Textur, Größe in Pixeln, Eingaben von Hand. */
    WebSurface surface();

    /** Linker Rand, in Bildschirmpunkten. */
    int x();

    /** Oberer Rand, in Bildschirmpunkten. */
    int y();

    /** Breite in Bildschirmpunkten. */
    int width();

    /** Höhe in Bildschirmpunkten. */
    int height();

    void moveTo(int x, int y);

    void resize(int width, int height);

    boolean visible();

    /** Unsichtbar heißt: nicht gezeichnet, aber offen — und ohne Fokus. */
    void setVisible(boolean visible);

    OverlayFocus focus();

    /**
     * Fokus geben oder nehmen.
     *
     * <p>Nur ein Overlay hat Fokus; wer ihn bekommt, nimmt ihn dem anderen.
     * Der Wechsel ist sofort wirksam, auch mitten in einer Taste: Was schon
     * gedrückt war, gilt für die Seite als losgelassen.
     */
    void focus(OverlayFocus wanted);

    /** Offen und mit lebendem Browser darunter. */
    boolean alive();

    @Override
    void close();
}
