package dev.devpanda.bamboocef.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Die Web-Runtime: hochfahren, Zustand nennen, herunterfahren.
 *
 * <p><b>Diese Klasse fasst Chromium nicht an.</b> Kein Import, kein Feld,
 * keine Signatur aus {@code org.cef} — der Unterbau kommt als
 * {@link WebBackend} herein. Der Grund ist kein Geschmack: Eine Klasse, die
 * einen solchen Typ nennt, verlangt ihn beim Laden. Wem die Laufzeitumgebung
 * fehlt, der bekäme beim Start der Mod einen {@code NoClassDefFoundError} —
 * und ein fehlender Browser, der den Client zerlegt, ist mehr als ein
 * fehlender Browser.
 *
 * <p><b>Nichts hier wirft.</b> Jeder Weg endet in einem
 * {@link WebRuntimeStatus}, auch der, an dem etwas kaputt ist. Das ist die
 * Zusicherung, unter der die Runtime überhaupt in diese Mod darf: Ein Browser,
 * der nicht startet, kostet eine Oberfläche und nicht das Spiel.
 *
 * <p>Der Zustand wird <b>einmal</b> ermittelt und gemerkt. Wer es erneut
 * versuchen will — nach einem nachgeholten Download etwa —, ruft
 * {@link #retry(Supplier)}; ob sich das lohnt, sagt
 * {@link WebRuntimeState#worthRetrying()}.
 */
public final class WebRuntime {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/Web");

    private static WebRuntimeStatus status = WebRuntimeStatus.of(WebRuntimeState.NOT_STARTED);
    private static WebBackend backend;

    private WebRuntime() {
    }

    /**
     * Fährt hoch, falls noch nicht geschehen.
     *
     * <p>Der Aufrufer reicht herein, wie ein Unterbau entsteht. Diese Klasse
     * weiß nicht, was dahintersteht — und soll es nicht wissen.
     *
     * @param start baut den Unterbau, oder wirft
     */
    public static synchronized WebRuntimeStatus start(Supplier<WebBackend> start) {
        if (status.state() != WebRuntimeState.NOT_STARTED) {
            return status;
        }
        return attempt(start);
    }

    /**
     * Noch einmal, nachdem sich etwas geändert haben könnte.
     *
     * <p>Nur sinnvoll, wenn der letzte Zustand einen zweiten Versuch wert war.
     * Eine fehlende Plattform wird auch beim zehnten Mal nicht auftauchen.
     */
    public static synchronized WebRuntimeStatus retry(Supplier<WebBackend> start) {
        if (!status.state().worthRetrying()) {
            return status;
        }
        return attempt(start);
    }

    private static WebRuntimeStatus attempt(Supplier<WebBackend> start) {
        try {
            WebBackend built = start.get();
            if (built == null) {
                return remember(WebRuntimeStatus.of(WebRuntimeState.FAILED,
                        "Der Unterbau kam ohne Grund nicht zustande"));
            }
            backend = built;
            return remember(WebRuntimeStatus.of(WebRuntimeState.READY, built.name()));
        } catch (WebRuntimeUnavailable known) {
            // Ein Zustand, den jemand vorhergesehen hat — der übliche Fall.
            return remember(known.status());
        } catch (Throwable broken) {
            // Alles andere: fehlende Systembibliothek, ein Archiv, das sich
            // nicht auspacken ließ, ein NoClassDefFoundError aus org.cef.
            // Throwable und nicht Exception — genau die Fehler, die den Client
            // sonst mitnehmen, sind keine Exceptions.
            LOG.warn("Die Web-Runtime ist nicht hochgekommen; das Spiel läuft ohne sie", broken);
            return remember(WebRuntimeStatus.of(WebRuntimeState.FAILED,
                    broken.getClass().getSimpleName() + ": " + broken.getMessage()));
        }
    }

    private static WebRuntimeStatus remember(WebRuntimeStatus found) {
        status = found;
        if (found.usable()) {
            LOG.info("Web-Runtime bereit ({})", found.reason());
        } else {
            LOG.info("Keine Web-Runtime — {}", found);
        }
        return found;
    }

    /** Woran man ist. */
    public static synchronized WebRuntimeStatus status() {
        return status;
    }

    /** Kann gerade ein Browser entstehen? */
    public static synchronized boolean isAvailable() {
        return status.usable() && backend != null;
    }

    /** Der Unterbau, oder {@code null}. Nur für die, die ihn wirklich brauchen. */
    public static synchronized WebBackend backend() {
        return isAvailable() ? backend : null;
    }

    /**
     * Fährt herunter.
     *
     * <p>Auch das darf nicht werfen: Beim Beenden des Spiels ist eine
     * Ausnahme aus einem Browser das Letzte, was jemand sehen will.
     */
    /**
     * Wie lange auf Chromiums Bestätigungen gewartet wird.
     *
     * <p>Großzügig genug für den Normalfall — im Prüfstand kam die Bestätigung
     * nach wenigen Runden — und kurz genug, dass niemand denkt, das Spiel
     * hänge. Ohne Frist könnte ein einziger Browser, der nie bestätigt, das
     * Beenden für immer aufhalten.
     */
    private static final long CLOSE_TIMEOUT_MILLIS = 2000;

    public static synchronized void shutdown() {
        // <b>Erst zurücksetzen, dann schließen.</b> Andersherum bliebe nach
        // einem gescheiterten Start der alte Grund für immer stehen: Es gäbe
        // keinen Unterbau zu schließen, und die Methode käme zurück, ohne den
        // Zustand anzufassen. Ein zweiter Versuch wäre danach unmöglich.
        WebBackend closing = backend;
        backend = null;
        status = WebRuntimeStatus.of(WebRuntimeState.NOT_STARTED);

        // <b>Die Reihenfolge ist der ganze Inhalt dieser Methode.</b>
        //
        //   1. alle Browser bitten, zuzugehen
        //   2. weiterpumpen, bis Chromium jede Schließung bestätigt hat
        //   3. erst dann den Unterbau abräumen
        //
        // Wer bei 3 anfängt, räumt CEF ab, während es noch Browser schließen
        // wollte — und genau daraus entstehen die Hilfsprozesse, die
        // stehenbleiben. close(true) ist eine Bitte, keine Tat.
        //
        // <b>Das läuft im Renderthread</b>, und das muss es: Gepumpt wird
        // dort, und nur wer pumpt, bekommt die Bestätigungen zu sehen.
        LOG.info("Web-Runtime fährt herunter — im Thread {}, offen: {}",
                Thread.currentThread().getName(), BrowserManager.count());
        try {
            BrowserManager.closeAll();
            if (BrowserManager.pending() > 0) {
                BrowserManager.awaitClosed(CLOSE_TIMEOUT_MILLIS,
                        dev.devpanda.bamboocef.web.runtime.WebPump::frame);
            }
        } catch (Throwable broken) {
            LOG.warn("Beim Schließen der Browser ging etwas schief", broken);
        }

        if (closing == null) {
            return;
        }
        try {
            closing.close();
        } catch (Throwable broken) {
            LOG.warn("Beim Herunterfahren der Web-Runtime ging etwas schief", broken);
        }
        LOG.info("Web-Runtime ist unten — offen: {}, ohne Bestätigung: {}",
                BrowserManager.count(), BrowserManager.pending());
    }
}
