package dev.devpanda.bamboocef.web;

/**
 * The state of the runtime together with a reason.
 *
 * <p>The reason is for humans and goes into the log — it is not an error code
 * that gets evaluated. Whoever wants to branch asks the
 * {@link WebRuntimeState}.
 *
 * @param state  where things stand
 * @param reason a sentence about it, or empty
 */
public record WebRuntimeStatus(WebRuntimeState state, String reason) {

    public WebRuntimeStatus {
        if (state == null) {
            throw new IllegalArgumentException("Ein Zustand ohne Zustand");
        }
        reason = reason == null ? "" : reason;
    }

    public static WebRuntimeStatus of(WebRuntimeState state) {
        return new WebRuntimeStatus(state, "");
    }

    public static WebRuntimeStatus of(WebRuntimeState state, String reason) {
        return new WebRuntimeStatus(state, reason);
    }

    public boolean usable() {
        return state.usable();
    }

    @Override
    public String toString() {
        return reason.isEmpty() ? state.name() : state.name() + ": " + reason;
    }
}
