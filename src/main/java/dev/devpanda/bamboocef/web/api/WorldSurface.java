package dev.devpanda.bamboocef.web.api;

/**
 * Eine Fläche in der Welt: an Ort, Ausrichtung und Größe gehängt.
 *
 * <p><b>Ort und Größe in Blöcken, Ausrichtung in Grad.</b> Der Ort ist der
 * Mittelpunkt der Fläche in Weltkoordinaten. Der Gierwinkel folgt
 * Minecrafts Konvention (Süden 0, Westen 90, Norden 180, Osten 270): Eine
 * Fläche mit {@code yaw = facing.toYRot()} schaut in dieselbe Richtung wie
 * ein Block mit dieser Ausrichtung — und ist von dort aus lesbar. Von hinten
 * zeigt sie ihr Spiegelbild, wie eine Scheibe.
 *
 * <p>Die Auflösung der Seite kommt aus dem {@link SurfaceSpec} in Pixeln;
 * die Ausdehnung in Blöcken ist davon unabhängig. Eine Fläche von zwei
 * Blöcken Breite mit 512 Pixeln ist scharf, eine von zwanzig nicht.
 *
 * <p><b>Was die Entfernung entscheidet.</b> Bis zwölf Blöcke malt die Seite
 * mit mittlerem Takt, bis {@link #maxDistance()} langsam, dahinter gar
 * nicht — und wird auch nicht gezeichnet. Das entscheidet die Entfernung
 * zur Kamera, nicht die Blickrichtung: Eine Seite hinter dem Spieler läuft
 * weiter, eine ferne steht still.
 *
 * <p>Eingaben gibt es hier nicht; die Fläche darunter nimmt sie von Hand
 * entgegen ({@link #surface()}), wer sie ihr geben will.
 *
 * <p>Fassung 1. Was hier steht, bleibt.
 */
public interface WorldSurface extends AutoCloseable {

    WebSurface surface();

    double x();

    double y();

    double z();

    void moveTo(double x, double y, double z);

    /** Gierwinkel in Grad, Minecrafts Konvention. */
    float yaw();

    /** Neigung in Grad; positiv kippt die Oberkante nach hinten. */
    float pitch();

    void rotate(float yaw, float pitch);

    /** Breite in Blöcken. */
    float width();

    /** Höhe in Blöcken. */
    float height();

    void resize(float width, float height);

    /** Ab dieser Entfernung zur Kamera, in Blöcken, ruht die Seite. */
    double maxDistance();

    void maxDistance(double blocks);

    boolean visible();

    /** Unsichtbar heißt: nicht gezeichnet, und die Seite ruht. */
    void setVisible(boolean visible);

    boolean alive();

    @Override
    void close();
}
