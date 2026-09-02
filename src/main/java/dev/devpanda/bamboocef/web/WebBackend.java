package dev.devpanda.bamboocef.web;

/**
 * What the runtime needs from its backend.
 *
 * <p><b>An interface for a backend that exists only once</b> — that looks like
 * speculation and isn't. It is the point at which the runtime can be loaded
 * <i>without Chromium having to be present</i>. Were a type from
 * {@code org.cef} named here, merely loading {@link WebRuntime} would raise a
 * {@code NoClassDefFoundError} — and the mod would no longer start just because
 * a runtime is missing that weighs 379 megabytes and ships separately.
 *
 * <p>That held in the MCEF days, when the backend was a foreign mod, and it
 * holds all the more since: it is ours now, but it still does not sit in the
 * jar.
 *
 * <p>That it can carry a second backend — a GPU path, say — is a side effect
 * and not the purpose.
 */
public interface WebBackend extends AutoCloseable {

    /** The name for log lines. */
    String name();

    /** Shuts down and releases everything. May be called more than once. */
    @Override
    void close();
}
