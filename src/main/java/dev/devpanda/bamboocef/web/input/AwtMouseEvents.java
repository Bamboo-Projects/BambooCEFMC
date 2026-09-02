package dev.devpanda.bamboocef.web.input;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Mausereignisse für Chromium, gebaut aus dem, was Minecraft meldet.
 *
 * <p><b>Warum die Maus bei AWT bleibt, die Tastatur aber nicht.</b> Der native
 * Teil liest bei der Maus ausschließlich öffentliche Methoden — {@code getID},
 * {@code getX}, {@code getY}, {@code getClickCount}, {@code getButton},
 * {@code getModifiersEx}, beim Rad zusätzlich {@code getScrollType},
 * {@code getWheelRotation} und {@code getUnitsToScroll}. Alles davon lässt
 * sich über die Konstruktoren setzen. Bei der Tastatur ist genau das nicht so:
 * Dort hängt alles an einem privaten Feld, das nur der native AWT-Code füllt.
 *
 * <p><b>Kein {@code MOUSE_CLICKED}.</b> Chromium baut den Klick aus Herunter
 * und Herauf. Ein zusätzliches Ereignis wäre ein zweiter Klick und verfälschte
 * Doppel- und Dreifachklicks.
 */
public final class AwtMouseEvents {

    /**
     * Minecrafts Tastennummer in AWTs Zählung.
     *
     * <p>Mitte und rechts sind vertauscht — GLFW zählt in der Reihenfolge, in
     * der Mäuse ihre Tasten melden, AWT nach Bedeutung. Dieselbe Falle wie in
     * {@link MouseButtons}, nur mit anderen Zielwerten.
     */
    public static int toAwtButton(int minecraftButton) {
        return switch (minecraftButton) {
            case MouseButtons.MINECRAFT_LEFT -> MouseEvent.BUTTON1;
            case MouseButtons.MINECRAFT_RIGHT -> MouseEvent.BUTTON3;
            case MouseButtons.MINECRAFT_MIDDLE -> MouseEvent.BUTTON2;
            default -> MouseEvent.NOBUTTON;
        };
    }

    /**
     * Die Flagge „diese Taste ist unten" für {@code getModifiersEx}.
     *
     * <p>CEF liest gedrückte Maustasten aus demselben Feld wie Strg und
     * Umschalt. Fehlt die Flagge, sieht eine Bewegung für Chromium aus wie
     * eine mit losgelassener Taste, und jedes Ziehen bricht ab.
     */
    public static int buttonDownMask(int minecraftButton) {
        return switch (minecraftButton) {
            case MouseButtons.MINECRAFT_LEFT -> InputEvent.BUTTON1_DOWN_MASK;
            case MouseButtons.MINECRAFT_RIGHT -> InputEvent.BUTTON3_DOWN_MASK;
            case MouseButtons.MINECRAFT_MIDDLE -> InputEvent.BUTTON2_DOWN_MASK;
            default -> 0;
        };
    }

    /**
     * Die zusammengesetzte Maske aus Minecrafts Weg in AWTs Familie.
     *
     * <p>Was hereinkommt, trägt zweierlei in einer Zahl: GLFWs Umschalttasten
     * in den unteren vier Bit und die gedrückten Maustasten in {@code 0x10},
     * {@code 0x20} und {@code 0x40} — die Flaggen, die {@link MouseButtons}
     * für Chromiums nativen Teil führt.
     *
     * <p><b>Die beiden Sätze überschneiden sich, und das ist kein Versehen,
     * das man hier beheben könnte.</b> GLFW belegt {@code 0x10} mit
     * Feststelltaste und {@code 0x20} mit Ziffernblocksperre — dieselben Bit
     * wie linke und mittlere Maustaste. Getrennt bleiben sie nur, weil GLFW
     * diese beiden Zustände ohne {@code GLFW_LOCK_KEY_MODS} gar nicht meldet.
     * Deshalb werden hier ausdrücklich nur die unteren vier Bit als
     * Umschalttasten gelesen: Was darüber liegt, gehört der Maus.
     */
    public static int toAwtModifiers(int combinedModifiers) {
        int awt = AwtModifiers.forKey(combinedModifiers & 0x0F);
        if ((combinedModifiers & MouseButtons.LEFT_MASK) != 0) {
            awt |= InputEvent.BUTTON1_DOWN_MASK;
        }
        if ((combinedModifiers & MouseButtons.MIDDLE_MASK) != 0) {
            awt |= InputEvent.BUTTON2_DOWN_MASK;
        }
        if ((combinedModifiers & MouseButtons.RIGHT_MASK) != 0) {
            awt |= InputEvent.BUTTON3_DOWN_MASK;
        }
        return awt;
    }

    /** Ob in der zusammengesetzten Maske überhaupt eine Maustaste steckt. */
    public static boolean anyButtonDown(int combinedModifiers) {
        return (combinedModifiers
                & (MouseButtons.LEFT_MASK | MouseButtons.MIDDLE_MASK | MouseButtons.RIGHT_MASK))
                != 0;
    }

    /**
     * Chromiums Tastennummer in AWTs Zählung.
     *
     * <p>Die Session hat bereits nach {@link MouseButtons#toBrowserButton}
     * übersetzt; hier geht es nur noch eine Zählung weiter.
     */
    public static int browserButtonToAwt(int cefButton) {
        return switch (cefButton) {
            case 0 -> MouseEvent.BUTTON1;
            case 1 -> MouseEvent.BUTTON2;
            case 2 -> MouseEvent.BUTTON3;
            default -> MouseEvent.NOBUTTON;
        };
    }

    /**
     * Eine Bewegung.
     *
     * @param dragging ob dabei eine Taste unten ist — Chromium unterscheidet
     *                 Bewegen und Ziehen, und eine Textauswahl entsteht nur
     *                 beim Ziehen
     */
    public static MouseEvent moved(int x, int y, int awtModifiers, boolean dragging) {
        return new MouseEvent(AwtEventSource.SOURCE,
                dragging ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false, MouseEvent.NOBUTTON);
    }

    /**
     * Der Zeiger verlässt die Fläche.
     *
     * <p>Ohne dieses Ereignis nimmt Chromium Schwebezustände nicht zurück, und
     * der zuletzt berührte Knopf bleibt für immer hell.
     */
    public static MouseEvent exited(int x, int y, int awtModifiers) {
        return new MouseEvent(AwtEventSource.SOURCE, MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false, MouseEvent.NOBUTTON);
    }

    /**
     * Eine Taste geht herunter oder herauf.
     *
     * @param clickCount 1, 2 oder 3 — daran und nur daran erkennt Chromium
     *                   einen Doppelklick. Den Wert liefert
     *                   {@link ClickCounter}.
     */
    public static MouseEvent button(int x, int y, boolean down, int minecraftButton,
            int clickCount, int awtModifiers) {
        return awtButton(x, y, down, toAwtButton(minecraftButton), clickCount, awtModifiers);
    }

    /** Dasselbe, wenn die Tastennummer schon AWTs ist. */
    public static MouseEvent awtButton(int x, int y, boolean down, int awtButton, int clickCount,
            int awtModifiers) {
        return new MouseEvent(AwtEventSource.SOURCE,
                down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), awtModifiers, x, y, Math.max(1, clickCount), false,
                awtButton);
    }

    /**
     * Eine Raststellung des Rades.
     *
     * <p><b>Das Vorzeichen geht unverändert durch — gemessen, nicht geraten.</b>
     * Drei Zählweisen treffen hier aufeinander: GLFW meldet nach oben ein
     * positives Delta, AWT nach oben ein negatives {@code wheelRotation}, und
     * Chromiums {@code deltaY} ist nach oben ebenfalls negativ. Zwei
     * Vorzeichenwechsel, die sich aufheben — aber das ist eine Behauptung, bis
     * jemand nachsieht. {@code tools/runtime/probe/KeyProbe.java} hat
     * nachgesehen:
     *
     * <pre>
     *   wheelRotation +1  →  deltaY -2.0   in der Seite: hinauf
     *   wheelRotation -1  →  deltaY +2.0   in der Seite: hinunter
     * </pre>
     *
     * <p>Minecrafts Delta ist beim Drehen nach oben positiv. Es wandert
     * deshalb unverändert in {@code wheelRotation}.
     *
     * @param notches      Minecrafts Raststellungen, positiv nach oben
     * @param unitsPerNotch Zeilen je Rastung. Der Wert steht im
     *                      {@code UnitsToScroll} und überschreibt dort das
     *                      Delta; drei ist der Wert, den MCEF verwendet hat.
     */
    public static MouseWheelEvent wheel(int x, int y, int notches, int unitsPerNotch,
            int awtModifiers) {
        return new MouseWheelEvent(AwtEventSource.SOURCE, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), awtModifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, unitsPerNotch, notches);
    }

    private AwtMouseEvents() {}
}
