package dev.devpanda.bamboocef.web;

/**
 * Der Zustand der Runtime samt Begründung.
 *
 * <p>Die Begründung ist für Menschen und steht im Protokoll — sie ist kein
 * Fehlercode, der ausgewertet wird. Wer verzweigen will, fragt den
 * {@link WebRuntimeState}.
 *
 * @param state  woran man ist
 * @param reason ein Satz dazu, oder leer
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
