package dev.devpanda.bamboocef.web.input;

/**
 * Maustasten von Minecraft nach Chromium — und die Merkliste, was gedrückt ist.
 *
 * <p><b>Die Nummern stimmen nicht überein, und das ist keine Marotte.</b>
 * GLFW zählt nach der Reihenfolge, in der Mäuse ihre Tasten melden:
 *
 * <pre>
 *   GLFW   0 = links    1 = rechts   2 = mitte
 *   CEF    0 = links    1 = mitte    2 = rechts
 * </pre>
 *
 * <p>Mitte und rechts sind vertauscht. Wer das übersieht, öffnet mit der
 * rechten Maustaste keine Kontextmenüs, sondern fügt Text ein — das ist das
 * Verhalten der mittleren Taste unter X11 und sieht aus wie ein Fehler in der
 * Seite.
 *
 * <p>Nachgesehen im nativen Teil von JCEF: Dort wird {@code getButton()} gegen
 * {@code GLFW_MOUSE_BUTTON_1/2/3} verglichen und daraus
 * {@code MBT_LEFT/MIDDLE/RIGHT} gemacht.
 *
 * <p><b>Und warum eine Merkliste nötig ist.</b> CEF führt gedrückte Maustasten
 * als Zusatzflaggen mit — dieselben Flaggen, in denen auch Strg und Umschalt
 * stehen. Eine Mausbewegung ohne diese Flaggen sieht für Chromium aus wie eine
 * Bewegung mit losgelassener Taste, und jedes Ziehen bricht ab: Text lässt
 * sich nicht markieren, ein Schieberegler springt zurück.
 */
public final class MouseButtons {

    /** Wie Minecraft und GLFW zählen. */
    public static final int MINECRAFT_LEFT = 0;
    public static final int MINECRAFT_RIGHT = 1;
    public static final int MINECRAFT_MIDDLE = 2;

    /**
     * Die Flaggen für gedrückte Tasten.
     *
     * <p>Die Werte stehen im nativen Teil von JCEF fest verdrahtet
     * ({@code modifiers & 0x10} und so fort) und stimmen mit
     * {@code CefMouseEvent.BUTTON1_MASK} überein. Sie stehen hier noch einmal,
     * damit diese Klasse ohne Chromium auf dem Klassenpfad geprüft werden
     * kann.
     */
    public static final int LEFT_MASK = 0x10;
    public static final int MIDDLE_MASK = 0x20;
    public static final int RIGHT_MASK = 0x40;

    private int pressedMask;

    /**
     * Übersetzt eine Minecraft-Tastennummer in Chromiums Zählung.
     *
     * @return die CEF-Nummer, oder {@code -1} für eine Taste, die Chromium
     *         nicht kennt — die vierte und fünfte Maustaste gehören nicht zum
     *         Vertrag und würden nativ verworfen
     */
    public static int toBrowserButton(int minecraftButton) {
        return switch (minecraftButton) {
            case MINECRAFT_LEFT -> 0;
            case MINECRAFT_RIGHT -> 2;
            case MINECRAFT_MIDDLE -> 1;
            default -> -1;
        };
    }

    /** Die Flagge zu einer Minecraft-Tastennummer, oder null für unbekannte. */
    public static int maskOf(int minecraftButton) {
        return switch (minecraftButton) {
            case MINECRAFT_LEFT -> LEFT_MASK;
            case MINECRAFT_RIGHT -> RIGHT_MASK;
            case MINECRAFT_MIDDLE -> MIDDLE_MASK;
            default -> 0;
        };
    }

    /** Merkt sich, dass diese Taste jetzt unten ist. */
    public void press(int minecraftButton) {
        pressedMask |= maskOf(minecraftButton);
    }

    /** Merkt sich, dass diese Taste wieder oben ist. */
    public void release(int minecraftButton) {
        pressedMask &= ~maskOf(minecraftButton);
    }

    /**
     * Alle gedrückten Tasten als Flaggen, zusammen mit den Umschalttasten.
     *
     * <p>Beides muss in dieselbe Zahl: CEF liest Maustasten und Umschalttasten
     * aus demselben Feld.
     */
    public int modifiersWith(int keyboardModifiers) {
        return pressedMask | keyboardModifiers;
    }

    /** Ist überhaupt eine Taste unten? */
    public boolean anyPressed() {
        return pressedMask != 0;
    }

    /**
     * Vergisst alle gedrückten Tasten.
     *
     * <p>Beim Verlassen der Fläche und beim Schließen: Eine Taste, die nach
     * dem Loslassen noch als gedrückt gilt, hängt für Chromium bis in alle
     * Ewigkeit in einem Ziehvorgang fest.
     */
    public void forget() {
        pressedMask = 0;
    }
}
