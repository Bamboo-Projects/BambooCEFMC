package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.capture.FrameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Meldet Chromium an, wer {@code mc://frame/…} beantwortet.
 *
 * <p><b>Nach der Initialisierung, nicht davor — und das ist belegt.</b> MCEF
 * meldet sein eigenes {@code mod://} unmittelbar nach {@code CefUtil.init()}
 * an, im selben Aufruf, in dem es Chromium hochfährt. Ein Schema-Handler
 * lässt sich also nachträglich anmelden; das ist keine Vermutung, sondern der
 * Weg, den MCEF selbst geht. Und er trägt auch für ein Bild <b>innerhalb</b>
 * einer Seite, die von woanders kommt — auch das ist nachgewiesen.
 *
 * <p><b>Ein Ablageort für die ganze Sitzung, und das ist keine Bequemlichkeit.</b>
 * Chromium nimmt eine Anmeldung je Schema entgegen, und was dabei hinterlegt
 * wird, gilt bis zum Ende. Wer später einen zweiten Ablageort anmeldet,
 * bekommt ein freundliches „ja" und wird trotzdem nie gefragt: Die zuerst
 * eingetragene Fabrik bleibt stehen.
 *
 * <p>Genau das ist hier passiert und hat teuer gekostet. Der Nachweis meldete
 * seinen eigenen Ablageort an, räumte ihn beim Schließen ab — und die Messung
 * danach bekam nie ein Bild, weil an der Anmeldung noch der leergeräumte
 * Ablageort hing. Der Fehler sah aus wie ein Adressproblem und war keines;
 * zwei Korrekturen an der Adressform gingen ins Leere, bevor die Ursache
 * dastand. Was ihn schließlich zeigte, war Chromiums eigene Konsole.
 *
 * <p>Deshalb gibt es den Ablageort <b>hier</b> und nur einmal. Wer ein Bild
 * hineinlegen will, holt ihn sich mit {@link #store()}.
 */
public final class FrameSchemes {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/FrameSchemes");

    /** Der eine Ablageort. Er lebt so lange wie Chromiums Anmeldung. */
    private static final FrameStore STORE = new FrameStore();

    /**
     * Dieselbe Adresse ohne Nummer — für die Probe auf den Zwischenspeicher.
     *
     * <p>Chromium sieht dann jedes Mal dieselbe Adresse. Ob es trotzdem
     * nachlädt, entscheidet allein der Kopf {@code Cache-Control: no-store},
     * den der Ablageort mitschickt.
     */
    public static final String SCHEME_URL_WITHOUT_GENERATION =
            FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current";

    private static boolean registered;

    private FrameSchemes() {
    }

    /** Der Ablageort, in den jeder sein Bild legt. */
    public static FrameStore store() {
        return STORE;
    }

    /**
     * Meldet den Ablageort an, falls noch nicht geschehen.
     *
     * @return ob Chromium die Anmeldung angenommen hat
     */
    public static synchronized boolean register() {
        if (registered) {
            return true;
        }
        try {
            // Bei der Gelegenheit: Chromiums Konsole ins Protokoll. Was in der
            // Seite schiefgeht, soll nicht nur die Seite wissen — ohne diese
            // Zeile wäre der Fehler oben nie gefunden worden.
            WebConsole.attach();
            boolean accepted = CefHost.app().registerSchemeHandlerFactory(
                    FrameScheme.SCHEME, FrameScheme.DOMAIN,
                    (browser, frame, url, request) -> new FrameHandler(STORE));
            registered = accepted;
            LOG.info("Schema {}://{} angemeldet: {}",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, accepted);
            return accepted;
        } catch (Throwable broken) {
            LOG.warn("Schema {}://{} ließ sich nicht anmelden",
                    FrameScheme.SCHEME, FrameScheme.DOMAIN, broken);
            return false;
        }
    }

    /**
     * Die Adresse für das neueste Bild, mit Nummer gegen den Zwischenspeicher.
     *
     * <p>Welche Nummer darin steht, ist dem Ablageort gleichgültig — er gibt
     * immer das neueste Bild heraus. Sie ist nur dazu da, dass Chromium die
     * Adresse für eine neue hält.
     */
    public static String urlFor(long generation) {
        return FrameScheme.SCHEME + "://" + FrameScheme.DOMAIN + "/current?v=" + generation;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
