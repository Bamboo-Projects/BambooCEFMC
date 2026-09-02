package dev.devpanda.bamboocef.web;

/**
 * The runtime does not come up, and someone knows why.
 *
 * <p>The difference from every other failure: this one is foreseen. Whoever
 * throws it has already classified the state — missing mod, missing download,
 * unknown platform. Everything that is <b>not</b> thrown this way ends up as
 * {@link WebRuntimeState#FAILED} with the class name in the log.
 *
 * <p><b>No exception hierarchy.</b> One exception per state would be a lot of
 * classes for a distinction that an enum already makes.
 */
public class WebRuntimeUnavailable extends RuntimeException {

    private final transient WebRuntimeStatus status;

    public WebRuntimeUnavailable(WebRuntimeState state, String reason) {
        super(state + ": " + reason);
        this.status = WebRuntimeStatus.of(state, reason);
    }

    public WebRuntimeStatus status() {
        return status;
    }
}
