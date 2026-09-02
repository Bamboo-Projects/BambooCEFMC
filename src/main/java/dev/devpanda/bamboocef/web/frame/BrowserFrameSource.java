package dev.devpanda.bamboocef.web.frame;

/**
 * Woher Bilder kommen.
 *
 * <p>Heute ist das ein Browser, dessen {@code onPaint} in ein
 * {@link FrameSlot} legt. Später kann es dieselbe Quelle mit einer Textur auf
 * der Grafikkarte sein, ohne dass irgendetwas dahinter davon erfährt — deshalb
 * steht hier ein {@link BrowserFrame} und kein Puffer.
 */
public interface BrowserFrameSource {

    /**
     * Das neueste Bild, oder {@code null}.
     *
     * <p><b>Der Aufrufer besitzt es</b> und muss es schließen oder
     * zurückgeben. Zweimal hintereinander gerufen, ohne dass etwas Neues
     * gemalt wurde, antwortet die Quelle beim zweiten Mal mit {@code null} —
     * ein Bild wird nicht zweimal herausgegeben.
     */
    BrowserFrame poll();
}
