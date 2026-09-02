package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ohne Chromium sagt die Runtime das — und nimmt nichts mit.
 *
 * <p><b>Dieser Prüflauf steht genau in der Lage des Spielers ohne
 * Laufzeitumgebung.</b> Im Prüfstand liegt {@code org.cef} nicht auf dem
 * Klassenpfad: Das gebaute {@code jcef.jar} ist {@code compileOnly}, und das
 * reicht zum Übersetzen und nicht zum Laufen. Was hier passiert, passiert also
 * auch bei jedem, neben dessen Spiel die Laufzeitumgebung fehlt.
 *
 * <p>Die Zusicherung, um die es geht, ist die wichtigste der ganzen Runtime:
 * Eine fehlende Browser-Laufzeit kostet eine Oberfläche und nicht den Client.
 */
class WebSupportTest {

    @BeforeEach
    void reset() {
        WebRuntime.shutdown();
        // <b>Ein Manifest ohne Adresse.</b> Das echte im Klassenpfad nennt
        // seit der Auslieferung eine — und der Griff nach der Laufzeitumgebung
        // stieße damit aus dem Prüflauf heraus einen echten Download an, ins
        // Arbeitsverzeichnis von Gradle. Ohne Adresse endet er im Zustand,
        // den dieser Prüflauf sehen will.
        Properties offline = new Properties();
        offline.setProperty("runtime.version", "0.0-probe");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".archive", "probe.tar.gz");
        offline.setProperty("runtime." + RuntimeManifest.platform() + ".sha256", "0".repeat(64));
        RuntimeManifest.useForTests(offline);
    }

    @AfterEach
    void restore() {
        WebRuntime.shutdown();
        RuntimeManifest.useForTests(null);
    }

    @Test
    @DisplayName("Ohne Laufzeitumgebung gibt es einen Zustand statt eines Absturzes")
    void withoutRuntimeThereIsAnAnswerInsteadOfACrash() {
        WebRuntimeStatus status = WebSupport.ensureStarted();

        assertNotNull(status);
        assertFalse(status.usable(), "ohne Laufzeitumgebung kann nichts nutzbar sein");
        assertFalse(WebRuntime.isAvailable());

        // <b>Der Grund muss unverändert ankommen.</b> Der Griff nach der
        // Laufzeitumgebung findet den Ordner nicht und sagt das als
        // RUNTIME_MISSING; wer das unterwegs in FAILED umpackt, macht aus
        // „liegt nicht da" ein „kaputt" — und aus „wird geladen" ebenso.
        assertEquals(WebRuntimeState.RUNTIME_MISSING, status.state(),
                "der Grund muss unverändert durchkommen: " + status);
    }

    @Test
    @DisplayName("Und man kann es danach gefahrlos noch einmal versuchen")
    void tryingAgainIsHarmless() {
        WebSupport.ensureStarted();
        WebRuntimeStatus second = WebSupport.retry();

        assertFalse(second.usable());
        assertFalse(WebRuntime.isAvailable());
    }
}
