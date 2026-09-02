package dev.devpanda.bamboocef.web;

/**
 * What the {@link BrowserManager} needs to know about a session.
 *
 * <p><b>An interface for exactly one implementation</b> — that looks like
 * speculation and isn't. It exists so that {@code BrowserManager} can live in
 * {@code web} without knowing {@code web.runtime}: that is where Chromium
 * hangs, and whoever loads the manager should not have to load it along.
 */
public interface ManagedBrowser {

    /**
     * Closes the session. Must be safe to call more than once.
     *
     * <p>Chromium's acknowledgement — {@code onBeforeClose} — comes afterwards
     * and asynchronously. Whoever wants to know whether it has arrived asks the
     * manager.
     */
    void close();

    /** How the session should be named in the log. */
    String describe();
}
