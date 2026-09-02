package dev.devpanda.bamboocef.web;

import java.util.function.Supplier;

/**
 * Der eine Ort, an dem entschieden wird, worauf die Runtime läuft.
 *
 * <p>Es gibt genau einen Unterbau. Er steht trotzdem hier und nicht in
 * {@link WebRuntime}, aus einem Grund, der nichts mit Vorrat zu tun hat:
 * {@code WebRuntime} darf keinen Typ aus {@code org.cef} nennen, sonst
 * verlangt schon ihr Laden die native Laufzeitumgebung. Diese Klasse nennt ihn
 * auch nicht — sie reicht ein Lambda weiter, und erst dessen Aufruf lädt die
 * Klasse, die es tut.
 */
public final class WebSupport {

    private WebSupport() {
    }

    /**
     * Fährt die Runtime hoch, falls es geht.
     *
     * <p>Beim ersten Bedarf zu rufen und nicht beim Start des Spiels:
     * Chromium fährt im Renderthread hoch und braucht dafür ein paar hundert
     * Millisekunden. Wer zu früh fragt, bekommt ein „noch nicht" als „nein".
     */
    public static WebRuntimeStatus ensureStarted() {
        return WebRuntime.start(backend());
    }

    /** Noch einmal versuchen, wenn der letzte Grund einen zweiten Versuch wert war. */
    public static WebRuntimeStatus retry() {
        return WebRuntime.retry(backend());
    }

    private static Supplier<WebBackend> backend() {
        // Ein Lambda und kein Methodenverweis: Beide wären hier richtig, aber
        // der Rumpf macht sichtbar, dass die Klasse erst beim Aufruf geladen
        // wird — und genau darauf beruht, dass die Laufzeitumgebung fehlen
        // darf, ohne die Mod mitzunehmen.
        return () -> dev.devpanda.bamboocef.web.runtime.CefHost.backend();
    }
}
