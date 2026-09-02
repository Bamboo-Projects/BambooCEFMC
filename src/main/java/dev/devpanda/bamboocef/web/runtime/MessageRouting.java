package dev.devpanda.bamboocef.web.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Which session gets a message from the page.
 *
 * <p><b>By the browser's identity, not by its equality.</b> Two browsers can
 * look alike and yet be two; the mapping hangs on the identity itself. Hence an
 * {@link IdentityHashMap} and not an ordinary {@code equals}.
 *
 * <p>The core without Chromium: what comes in here is a key ({@code Object}) and
 * a receiver. The wiring to CEF lives in {@link WebMessages} — here only who
 * gets whom is set out, and that is testable without Chromium.
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
     * Hands a message to its receiver.
     *
     * @return whether there was one and the message had content
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
