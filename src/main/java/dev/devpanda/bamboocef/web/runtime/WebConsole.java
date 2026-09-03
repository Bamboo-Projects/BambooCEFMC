package dev.devpanda.bamboocef.web.runtime;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes Chromium's own messages into the log.
 *
 * <p><b>Because guessing is expensive.</b> When the background did not arrive,
 * the only hint was a broken-image icon in the corner of a screenshot. Chromium
 * knew all along what was going on and said it into its console — which no one
 * read.
 *
 * <p>The page's messages now take the same path as everything else. What goes
 * wrong in the web interface thus stands next to what goes wrong in Minecraft,
 * and on the same timeline.
 *
 * <p>The registration is on MCEF's client, not on the browser: the client is
 * the place where JCEF expects such handlers, and it then applies to every
 * browser.
 */
public final class WebConsole implements CefDisplayHandler {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/Web");

    private static boolean attached;

    private WebConsole() {
    }

    /** Hooks in, once per session. */
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
        // The wrong answer would be true: that would mean "handled" and take
        // the message away from the rest of Chromium.
        return false;
    }

    /** Shorten long file paths — the name is enough to recognize it. */
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
     * A page wants to enter fullscreen mode.
     *
     * <p><b>Without {@code @Override}, and that is deliberate.</b> Upstream
     * java-cef requires this method, the CinemaMod fork does not know it.
     * Without the annotation the same file satisfies both interfaces: there it
     * overrides a method, here it is an extra one that no one calls.
     *
     * <p>There is nothing to do. A fullscreen within a screen that itself
     * already takes up the whole surface is not a state change that concerns
     * us.
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
     * Cursor requests go through elsewhere.
     *
     * <p>The browser takes them in its own {@code onCursorChange} and passes
     * them on to the screen. Intervening here as well would mean having two
     * places that do the same thing.
     */
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        return false;
    }
}
