package dev.devpanda.bamboocef.web.input;

/**
 * Counts which click in a row this just was.
 *
 * <p><b>Why this has to be counted ourselves.</b> CEF does not derive double
 * clicks from timestamps — it trusts the number in the event. Whoever always
 * sends a one there never gets a double click: no word can be selected, no
 * field chosen by double click. In a text editor that is not a missing nicety
 * but a missing basic function.
 *
 * <p>MCEF always sends a one. This is one of the places where we deliberately
 * do something else.
 *
 * <p><b>Two conditions, both required.</b> Time alone is not enough: whoever
 * clicks twice quickly at far-apart spots means two clicks. Proximity alone is
 * not enough either: two clicks a minute apart are not a double click, however
 * precisely they land.
 *
 * <p>Pure computation — time comes in as a parameter so the process can be
 * checked without waiting.
 */
public final class ClickCounter {

    /** What Windows and most interfaces let pass as a double click. */
    public static final long DEFAULT_WINDOW_MILLIS = 500L;

    /**
     * How far off the second click may land, in browser pixels.
     *
     * <p>Not zero: a mouse always wanders a little when double clicking, and at
     * high resolution a few pixels are less than a letter.
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
     * Reports a button press and says which one in sequence it was.
     *
     * @return 1 for a single click, 2 for a double click, 3 for a triple click
     *         — after that the count starts over, because no interface
     *         distinguishes more than three
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
     * The count for the release matching the last press.
     *
     * <p>CEF expects the same number on press and release. Whoever sends a one
     * again on release breaks the double click halfway through.
     */
    public int released() {
        return Math.max(1, count);
    }

    /**
     * Forgets what was.
     *
     * <p>Needed when the pointer leaves the surface or the browser loses focus:
     * a click before leaving and one after returning are not two parts of the
     * same gesture, even if less than half a second lies between them.
     */
    public void forget() {
        button = -1;
        count = 0;
    }
}
