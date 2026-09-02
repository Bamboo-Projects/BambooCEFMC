package dev.devpanda.bamboocef.web.api;

/**
 * Eine lebende Web-Fläche.
 *
 * <p>Was sie zeigt, liegt in einer Textur; was auf ihr geschieht, kommt über
 * die Eingabemethoden herein. Alles davon gehört dem Renderthread — Chromiums
 * Sitzung lebt dort, und ein Aufruf von woanders ist kein Zufallsfehler,
 * sondern einer, der beim ersten Bild auffällt.
 *
 * <p><b>Kein Typ aus {@code org.cef} kommt hier vor</b>, und das ist die
 * ganze Absicht der Schnittstelle: Wer eine Fläche einbaut, übersetzt nicht
 * gegen Chromium, und die Fassung darunter darf wechseln.
 */
public interface WebSurface extends AutoCloseable {

    /**
     * Die Textur, in der das aktuelle Bild liegt.
     *
     * <p>Eine rohe Kennung von OpenGL. Für den Weltrenderpfad reicht das
     * nicht — dort braucht es eine {@code ResourceLocation}, und die gibt es
     * über {@link #textureLocation()}.
     *
     * @return die Kennung, oder {@code 0}, solange noch kein Bild da ist
     */
    int textureId();

    /**
     * Dieselbe Textur, angemeldet beim Texturverwalter.
     *
     * <p>Der Weg für alles, was über {@code RenderType} zeichnet. Die Adresse
     * gehört der Fläche und wird beim Schließen abgemeldet.
     */
    net.minecraft.resources.ResourceLocation textureLocation();

    int width();

    int height();

    /** Ändert die Größe. Chromium legt dabei eine neue Textur an. */
    void resize(int width, int height);

    /** Wie die Fläche heißt — im Protokoll und in Chromiums Liste. */
    String name();

    /** Läuft sie noch? */
    boolean alive();

    // ---- Eingaben -----------------------------------------------------------

    /**
     * Der Zeiger steht auf diesem Punkt.
     *
     * @param x Browserpixel, nicht Bildschirmpunkte
     */
    void mouseMoved(int x, int y, int modifiers);

    /** Der Zeiger verlässt die Fläche — sonst bleibt der letzte Knopf hell. */
    void mouseLeft(int x, int y);

    /** @param button Minecrafts Zählung: 0 links, 1 rechts, 2 Mitte */
    void mousePressed(int x, int y, int button, int modifiers);

    void mouseReleased(int x, int y, int button, int modifiers);

    void mouseScrolled(int x, int y, double amount, int modifiers);

    /**
     * Eine Taste geht herunter.
     *
     * @return wahr, wenn die Fläche sie genommen hat. Falsch heißt: Der Filter
     *         hat sie durchgelassen, und der Aufrufer soll sie dem Spiel
     *         überlassen.
     */
    boolean keyPressed(int glfwKey, int scanCode, int modifiers);

    boolean keyReleased(int glfwKey, int scanCode, int modifiers);

    /**
     * Ein getipptes Zeichen.
     *
     * <p>Getrennt von den Tasten, weil nur das Betriebssystem weiß, was auf
     * einer deutschen Tastatur ein Umlaut ist.
     *
     * @return wahr, wenn die Fläche es genommen hat
     */
    boolean charTyped(char typed, int modifiers);

    /** Hat die Fläche den Tastaturfokus? */
    void setFocused(boolean focused);

    /**
     * Wird gerufen, wenn Chromium einen anderen Mauszeiger möchte.
     *
     * <p>Die Zahl ist die Ordnungszahl aus {@code cef_cursor_type_t}. Wer
     * nichts anmeldet, bekommt den Zeiger des Spiels.
     */
    void onCursor(java.util.function.IntConsumer sink);

    /**
     * Nachrichten aus der Seite entgegennehmen.
     *
     * <p>In der Seite: {@code window.fnSend("...")} oder
     * {@code window.fnSend({...})} — eine Zeichenkette oder ein Objekt als
     * JSON. Der Empfänger läuft im Renderthread. {@code null} meldet ihn ab.
     *
     * <p>Damit schließt sich der Kreis: Ein Schnellmenü kann nicht nur Tasten
     * bekommen, sondern seine Wahl auch zurückgeben.
     */
    void onMessage(java.util.function.Consumer<String> handler);

    @Override
    void close();
}
