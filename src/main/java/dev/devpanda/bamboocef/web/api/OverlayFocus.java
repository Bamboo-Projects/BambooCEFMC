package dev.devpanda.bamboocef.web.api;

/**
 * Wem die Eingaben gehören, solange ein Overlay offen ist.
 *
 * <p><b>Drei Stufen und nicht zwei.</b> „Fokus ja oder nein“ kennt den
 * häufigsten Fall nicht: ein Schnellmenü, das Escape und die Pfeiltasten
 * nimmt, während der Spieler weiterläuft und sich umsieht. Dafür muss die
 * Maus beim Spiel bleiben und nur ein Teil der Tastatur zur Seite gehen.
 */
public enum OverlayFocus {

    /** Nur zu sehen. Jede Eingabe gehört dem Spiel. */
    NONE,

    /**
     * Tasten nach dem Filter der Fläche; die Maus bleibt beim Spiel.
     *
     * <p>Der Spieler sieht sich weiter um, läuft weiter, und was der Filter
     * nicht nimmt, kommt beim Spiel an, als gäbe es das Overlay nicht.
     */
    KEYBOARD,

    /**
     * Maus und Tasten. Der Zeiger ist frei und zeigt auf die Fläche.
     *
     * <p>Der Spieler steht derweil — wie in jeder Oberfläche mit freiem
     * Zeiger; NeoForge schaltet die Belegungen des Spiels ab, sobald ein
     * Bildschirm offen ist. Ein Klick neben die Fläche, Escape ohne Filter
     * oder F10 geben die Maus zurück.
     */
    MOUSE;

    /** Bekommt die Fläche in dieser Stufe Tasten? */
    public boolean takesKeys() {
        return this != NONE;
    }

    /** Bekommt die Fläche in dieser Stufe die Maus? */
    public boolean takesMouse() {
        return this == MOUSE;
    }
}
