package dev.devpanda.bamboocef.web.runtime;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leitet Chromiums eigene Meldungen ins Protokoll.
 *
 * <p><b>Weil Raten teuer ist.</b> Als der Hintergrund nicht ankam, war der
 * einzige Hinweis ein Bruchsymbol in der Ecke eines Bildschirmfotos. Chromium
 * wusste die ganze Zeit, was los war, und sagte es in seine Konsole — die
 * niemand las.
 *
 * <p>Meldungen der Seite gehen jetzt denselben Weg wie alles andere. Was in
 * der Weboberfläche schiefgeht, steht damit neben dem, was in Minecraft
 * schiefgeht, und in derselben Zeitachse.
 *
 * <p>Angemeldet wird an MCEFs Klienten, nicht am Browser: Der Klient ist der
 * Ort, an dem JCEF solche Handler erwartet, und er gilt dann für jeden
 * Browser.
 */
public final class WebConsole implements CefDisplayHandler {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Web");

    private static boolean attached;

    private WebConsole() {
    }

    /** Hängt sich ein, einmal je Sitzung. */
    public static synchronized void attach() {
        if (attached) {
            return;
        }
        try {
            CefHost.client().addDisplayHandler(new WebConsole());
            attached = true;
        } catch (Throwable broken) {
            LOG.warn("Chromiums Konsole ließ sich nicht anzapfen", broken);
        }
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                    String message, String source, int line) {
        String where = source == null || source.isEmpty()
                ? "" : " (" + shorten(source) + ":" + line + ")";
        switch (level) {
            case LOGSEVERITY_ERROR, LOGSEVERITY_FATAL -> LOG.warn("Seite: {}{}", message, where);
            case LOGSEVERITY_WARNING -> LOG.info("Seite (Warnung): {}{}", message, where);
            default -> LOG.info("Seite: {}{}", message, where);
        }
        // Falsch wäre wahr: Das hieße „behandelt" und nähme die Meldung dem
        // Rest von Chromium weg.
        return false;
    }

    /** Lange Dateipfade kürzen — der Name reicht zum Wiedererkennen. */
    private static String shorten(String source) {
        int slash = source.lastIndexOf('/');
        return slash >= 0 && slash < source.length() - 1 ? source.substring(slash + 1) : source;
    }

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
    }

    /**
     * Eine Seite will in den Vollbildmodus.
     *
     * <p><b>Ohne {@code @Override}, und das ist Absicht.</b> Upstream
     * java-cef verlangt diese Methode, der CinemaMod-Fork kennt sie nicht.
     * Ohne die Anmerkung erfüllt dieselbe Datei beide Schnittstellen: Dort
     * setzt sie eine Methode außer Kraft, hier ist sie eine zusätzliche, die
     * niemand ruft.
     *
     * <p>Zu tun gibt es nichts. Ein Vollbild innerhalb eines Bildschirms, der
     * selbst schon die ganze Fläche einnimmt, ist keine Zustandsänderung, die
     * uns betrifft.
     */
    public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
    }

    @Override
    public boolean onTooltip(CefBrowser browser, String text) {
        return false;
    }

    @Override
    public void onStatusMessage(CefBrowser browser, String value) {
    }

    /**
     * Zeigerwünsche gehen woanders durch.
     *
     * <p>Der Browser nimmt sie in seinem eigenen {@code onCursorChange}
     * entgegen und reicht sie an den Bildschirm weiter. Hier noch einmal
     * einzugreifen hieße, zwei Stellen zu haben, die dasselbe tun.
     */
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        return false;
    }
}
