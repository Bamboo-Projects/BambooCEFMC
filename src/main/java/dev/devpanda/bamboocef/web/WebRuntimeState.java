package dev.devpanda.bamboocef.web;

/**
 * Why there currently is a browser — or none.
 *
 * <p><b>One state per reason and not a {@code boolean}.</b> "Web does not work"
 * is no information: whoever is missing the runtime needs a different sentence
 * than whoever sits behind a corporate proxy, and both need a different one
 * than someone on a platform for which there are no binaries. A collapsed
 * {@code false} turns three questions into one that cannot be answered.
 *
 * <p>None of these states is a fault of the mod. The game runs on in every one
 * of them — what is missing is a user interface, not the network, not the
 * machines, not the language.
 */
public enum WebRuntimeState {

    /** Not yet attempted. The first state of every session. */
    NOT_STARTED,

    /**
     * The runtime does not sit next to the game.
     *
     * <p>Chromium weighs 379 megabytes and therefore is not in this mod's jar.
     * Whoever downloads it has a browser on the next start.
     *
     * <p>Set by {@link RuntimeInstall} when a folder named via
     * {@code fn.runtime.dir} is empty or the manifest knows no address to
     * download from.
     */
    RUNTIME_MISSING,

    /**
     * There are no binaries for this platform.
     *
     * <p>No reinstalling helps. This has to sound different from a missing
     * download, or someone searches for hours in the wrong place.
     *
     * <p>Set by {@link RuntimeInstall} when the manifest has no entry for this
     * platform. So far only {@code windows-x86_64} is built.
     */
    UNSUPPORTED,

    /**
     * Chromium is not yet on disk.
     *
     * <p>Set by {@link RuntimeInstall} as soon as the download has been kicked
     * off in the background. Behind a proxy, without a network, or when the
     * storage is down, it stays that way — and that is a state that can turn out
     * differently on the next attempt.
     */
    NOT_DOWNLOADED,

    /**
     * The start failed.
     *
     * <p>Everything that is left: missing system libraries, an aborted
     * download, a broken installation. The reason is in the
     * {@link WebRuntimeStatus}.
     */
    FAILED,

    /**
     * Chromium was there and has shut down.
     *
     * <p>A final state: CEF can be started only once per process. It arises
     * when the game is quitting, if a frame is still painted after shutdown and
     * someone wants a browser in the process — that one gets this information
     * instead of a restart attempt, which used to end as an error with a stack
     * trace in the log.
     */
    SHUT_DOWN,

    /** It is running. */
    READY;

    /** Can a browser be created in this state? */
    public boolean usable() {
        return this == READY;
    }

    /**
     * Is a second attempt worthwhile?
     *
     * <p>A missing download may be there next time, a missing platform never.
     * Whoever does not separate the two either tries forever or one time too
     * few.
     */
    public boolean worthRetrying() {
        return this == NOT_DOWNLOADED || this == FAILED;
    }
}
