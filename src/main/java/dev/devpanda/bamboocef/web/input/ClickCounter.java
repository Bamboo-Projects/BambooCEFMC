package dev.devpanda.bamboocef.web.input;

/**
 * Zählt, der wievielte Klick in Folge das gerade war.
 *
 * <p><b>Warum das selbst gezählt werden muss.</b> CEF leitet Doppelklicks
 * nicht aus Zeitstempeln ab — es glaubt der Zahl, die im Ereignis steht. Wer
 * dort immer eine Eins schickt, bekommt nie einen Doppelklick: Kein Wort lässt
 * sich markieren, kein Feld durch Doppelklick auswählen. In einem Texteditor
 * ist das nicht eine fehlende Feinheit, sondern eine fehlende Grundfunktion.
 *
 * <p>MCEF schickt immer eine Eins. Das ist eine der Stellen, an denen wir
 * bewusst etwas anderes tun.
 *
 * <p><b>Zwei Bedingungen, beide nötig.</b> Zeit allein reicht nicht: Wer
 * zweimal schnell an weit entfernte Stellen klickt, meint zwei Klicks. Nähe
 * allein reicht auch nicht: Zwei Klicks im Abstand einer Minute sind keine
 * Doppelklicks, egal wie genau sie treffen.
 *
 * <p>Reine Rechnung — die Zeit kommt herein, damit sich der Ablauf prüfen
 * lässt, ohne zu warten.
 */
public final class ClickCounter {

    /** Was Windows und die meisten Oberflächen als Doppelklick durchgehen lassen. */
    public static final long DEFAULT_WINDOW_MILLIS = 500L;

    /**
     * Wie weit der zweite Klick daneben liegen darf, in Browser-Pixeln.
     *
     * <p>Nicht null: Eine Maus wandert beim Doppelklicken immer ein wenig, und
     * bei hoher Auflösung sind ein paar Pixel weniger als ein Buchstabe.
     */
    public static final int DEFAULT_SLOP_PIXELS = 4;

    private final long windowMillis;
    private final int slopPixels;

    private int button = -1;
    private int lastX;
    private int lastY;
    private long lastMillis;
    private int count;

    public ClickCounter() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_SLOP_PIXELS);
    }

    public ClickCounter(long windowMillis, int slopPixels) {
        this.windowMillis = windowMillis;
        this.slopPixels = slopPixels;
    }

    /**
     * Meldet einen Tastendruck und sagt, der wievielte er war.
     *
     * @return 1 für einen einfachen Klick, 2 für einen Doppelklick, 3 für
     *         einen Dreifachklick — danach beginnt die Zählung von vorn, weil
     *         keine Oberfläche mehr als drei unterscheidet
     */
    public int pressed(int button, int x, int y, long nowMillis) {
        boolean continues = button == this.button
                && nowMillis - lastMillis <= windowMillis
                && Math.abs(x - lastX) <= slopPixels
                && Math.abs(y - lastY) <= slopPixels;

        count = continues ? count + 1 : 1;
        if (count > 3) {
            count = 1;
        }
        this.button = button;
        this.lastX = x;
        this.lastY = y;
        this.lastMillis = nowMillis;
        return count;
    }

    /**
     * Die Zahl für das Loslassen zum letzten Druck.
     *
     * <p>CEF erwartet dieselbe Zahl bei Druck und Loslassen. Wer beim
     * Loslassen wieder eine Eins schickt, macht den Doppelklick auf halbem Weg
     * wieder kaputt.
     */
    public int released() {
        return Math.max(1, count);
    }

    /**
     * Vergisst, was war.
     *
     * <p>Nötig, wenn der Zeiger die Fläche verlässt oder der Browser den Fokus
     * verliert: Ein Klick vor dem Verlassen und einer nach der Rückkehr sind
     * keine zwei Teile derselben Geste, auch wenn dazwischen weniger als eine
     * halbe Sekunde liegt.
     */
    public void forget() {
        button = -1;
        count = 0;
    }
}
