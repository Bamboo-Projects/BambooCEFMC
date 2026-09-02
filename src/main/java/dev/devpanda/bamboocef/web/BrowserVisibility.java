package dev.devpanda.bamboocef.web;

/**
 * Wie sichtbar ein Browser gerade ist — und wie viele Bilder er dafür bekommt.
 *
 * <p><b>Kein Browser bekommt, was er will.</b> Chromium malt so schnell es
 * kann; bei einem Dutzend Anzeigen in einer Basis ist das die schnellste Art,
 * eine Bildrate zu ruinieren. Was ein Browser wirklich braucht, hängt nicht an
 * ihm, sondern daran, wer ihn ansieht: Ein Editor unter den Fingern braucht
 * jeden Tastenanschlag sofort, eine Tafel am anderen Ende der Halle nicht.
 *
 * <p><b>Nach oben ist hier etwas zu holen — seit CEF 146.</b> Die Zahlen
 * gelten in beide Richtungen: {@code CefBrowserSettings.windowless_frame_rate}
 * nimmt sie entgegen, wir setzen sechzig, und gemessen kommen <b>60,3 Bilder
 * je Sekunde</b> an. Die frühere JCEF-Fassung kannte weder die Einstellung
 * noch einen Weg, sie zu übergeben; dort waren dreißig die Decke, und diese
 * Stufen konnten nur drosseln.
 *
 * <p>Die Zahlen unterhalb von {@link #FOREGROUND} sind ein Anfang und keine
 * Messung. Sie stehen hier, damit die Drosselung von Beginn an existiert —
 * nachträglich eingezogen wäre sie ein Umbau jeder Aufrufstelle.
 */
public enum BrowserVisibility {

    /**
     * Vollbild, unter den Fingern des Spielers.
     *
     * <p>Die Sechzig ist erreicht: Gemessen kommen 60,3 Bilder je Sekunde an.
     * Diese Stufe drosselt damit nichts — sie ist die Zahl, die beim Aufbau
     * des Browsers gesetzt wird.
     */
    FOREGROUND(60),

    /** Offen und sichtbar, aber nicht das, worauf jemand tippt. */
    ACTIVE(30),

    /** Eine Fläche in der Welt, nah genug zum Lesen. */
    NEARBY(15),

    /** Zu weit weg, um Text zu erkennen — es reicht, dass sich etwas regt. */
    DISTANT(5),

    /**
     * Niemand sieht hin.
     *
     * <p>Null und nicht eins: Ein Browser, der niemandem etwas zeigt, soll
     * nichts kosten. Chromium wird zusätzlich als unsichtbar gemeldet — in
     * dieser JCEF-Fassung heißt das {@code setWindowVisibility(false)} —, dann
     * hören auch seine eigenen Zeitgeber auf.
     */
    HIDDEN(0);

    private final int framesPerSecond;

    BrowserVisibility(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
    }

    public int framesPerSecond() {
        return framesPerSecond;
    }

    /** Der Abstand zwischen zwei Bildern in Nanosekunden, oder {@code -1} für nie. */
    public long frameIntervalNanos() {
        return framesPerSecond <= 0 ? -1L : 1_000_000_000L / framesPerSecond;
    }

    /** Soll dieser Browser überhaupt noch malen? */
    public boolean drawing() {
        return framesPerSecond > 0;
    }
}
