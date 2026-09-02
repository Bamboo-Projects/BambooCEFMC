package dev.devpanda.bamboocef.web.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Welche Sitzung eine Nachricht der Seite bekommt.
 *
 * <p><b>Über die Kennung des Browsers, nicht über seine Gleichheit.</b> Zwei
 * Browser können gleich aussehen und sind doch zwei; die Zuordnung hängt an
 * der Kennung selbst. Deshalb eine {@link IdentityHashMap} und kein
 * gewöhnliches {@code equals}.
 *
 * <p>Der Kern ohne Chromium: Was hier hereinkommt, ist ein Schlüssel
 * ({@code Object}) und ein Empfänger. Die Verdrahtung an CEF liegt in
 * {@link WebMessages} — hier steht nur, wer wen bekommt, und das ist ohne
 * Chromium prüfbar.
 */
final class MessageRouting {

    private final Map<Object, Consumer<String>> sinks = new IdentityHashMap<>();

    synchronized void register(Object browser, Consumer<String> sink) {
        sinks.put(browser, sink);
    }

    synchronized void unregister(Object browser) {
        sinks.remove(browser);
    }

    synchronized void clear() {
        sinks.clear();
    }

    /**
     * Reicht eine Nachricht an ihren Empfänger.
     *
     * @return ob es einen gab und die Nachricht Inhalt hatte
     */
    boolean dispatch(Object browser, String message) {
        if (message == null) {
            return false;
        }
        Consumer<String> sink;
        synchronized (this) {
            sink = sinks.get(browser);
        }
        if (sink == null) {
            return false;
        }
        sink.accept(message);
        return true;
    }
}
