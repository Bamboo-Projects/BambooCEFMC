package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.WebBackend;
import dev.devpanda.bamboocef.web.WebRuntimeState;
import dev.devpanda.bamboocef.web.WebRuntimeUnavailable;

/**
 * Der Unterbau auf der eigenen Laufzeitumgebung.
 *
 * <p><b>Der Gegenpart zu {@code McefBackend}, und bewusst schmaler.</b> MCEF
 * fährt sich nebenher selbst hoch; hier fahren wir selbst. Das ist ein
 * Unterschied, der sich bis in die Fehlermeldungen zieht: MCEF meldet mit
 * einem einzigen boolean, ob es hochkam, und nennt keinen Grund — unser Weg
 * kann sagen, was fehlt.
 *
 * <p><b>Für den Prüflauf, nicht für die Auslieferung.</b> Es gibt keinen
 * Download, keine Prüfsummen und keinen Wächter über stehengebliebene
 * Hilfsprozesse. Der Ordner muss dasein, sonst hört es hier auf. Was daraus
 * einmal wird — Verteilung, geordnetes Abschalten, ein Wächter —, ist ein
 * eigener Schritt und nicht dieser.
 */
public final class FnRuntimeBackend implements WebBackend {

    private FnRuntimeBackend() {
    }

    /**
     * Fährt die Laufzeitumgebung hoch.
     *
     * <p><b>Aus dem Renderthread zu rufen.</b> CEF verlangt, dass
     * Initialisierung, Nachrichtenschleife und Herunterfahren in demselben
     * Thread liegen, und unser Renderpfad verlangt, dass es der Renderthread
     * ist.
     *
     * @throws WebRuntimeUnavailable mit dem Grund, wenn es nicht geht
     */
    public static WebBackend create() {
        try {
            FnCefRuntime.ensureStarted();
        } catch (WebRuntimeUnavailable known) {
            // <b>Unverändert weiter.</b> Der Griff nach der Laufzeitumgebung
            // sagt, was fehlt — liegt nicht da, wird geladen, keine
            // Binärdateien, schon heruntergefahren. In FAILED umgepackt käme
            // davon nur der Text an, und der Zustand, an dem Oberfläche und
            // Wiederholung hängen, wäre weg.
            throw known;
        } catch (Throwable broken) {
            String reason = broken.getMessage() == null ? broken.toString() : broken.getMessage();
            throw new WebRuntimeUnavailable(WebRuntimeState.FAILED, reason);
        }
        // Chromiums Konsole ins Protokoll — für jede Seite, nicht nur für die
        // mit Hintergrund. Vorher hing das am Anmelden des Bildschemas, und
        // Overlays und Tafeln liefen stumm.
        WebConsole.attach();
        WebMessages.attach();
        return new FnRuntimeBackend();
    }

    @Override
    public String name() {
        Thread owner = FnCefRuntime.owner();
        return "eigene Laufzeitumgebung (Thread " + (owner == null ? "?" : owner.getName()) + ")";
    }

    @Override
    public void close() {
        // <b>Anders als bei MCEF gehört uns dieser Unterbau.</b> Dort war das
        // Nichtstun richtig, weil andere Mods denselben Browser mitbenutzen.
        // Hier gibt es niemanden sonst — und ein nicht heruntergefahrenes CEF
        // lässt Hilfsprozesse stehen.
        FnCefRuntime.shutdown();
    }
}
