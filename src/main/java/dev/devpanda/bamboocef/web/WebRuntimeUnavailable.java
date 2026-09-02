package dev.devpanda.bamboocef.web;

/**
 * Die Runtime kommt nicht hoch, und jemand weiß warum.
 *
 * <p>Der Unterschied zu jedem anderen Fehler: Dieser hier ist vorhergesehen.
 * Wer ihn wirft, hat den Zustand schon eingeordnet — fehlende Mod, fehlender
 * Download, unbekannte Plattform. Alles, was <b>nicht</b> so geworfen wird,
 * landet als {@link WebRuntimeState#FAILED} mit dem Klassennamen im Protokoll.
 *
 * <p><b>Keine Fehlerhierarchie.</b> Eine Ausnahme je Zustand wäre eine Menge
 * Klassen für eine Unterscheidung, die ein Enum schon trifft.
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
