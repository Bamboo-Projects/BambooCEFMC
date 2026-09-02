package dev.devpanda.bamboocef.web.runtime;

import dev.devpanda.bamboocef.web.WebBackend;
import org.cef.CefApp;
import org.cef.CefClient;

/**
 * Woher Chromium kommt — in dieser Fassung: aus unserer eigenen
 * Laufzeitumgebung.
 *
 * <p><b>Diese Klasse lag bis B8 zweimal vor</b>, unter demselben Namen, in
 * zwei Quellverzeichnissen — eine gegen MCEF, eine gegen upstream java-cef.
 * Ein Schalter im Buildskript entschied. Seit MCEF draußen ist, gibt es nur
 * noch {@code src/runtime/java}, und der Schalter ist weg.
 *
 * <p><b>Warum es überhaupt zwei Quellverzeichnisse waren und keine
 * Verzweigung.</b> MCEF und upstream java-cef bringen beide das vollständige
 * Paket {@code org.cef} mit — 170 Klassen, gleiche Namen, anderer Inhalt. Sie
 * können nicht nebeneinander im Klassenpfad liegen. Damit war jede Lösung
 * innerhalb einer Übersetzungseinheit ausgeschlossen; die Trennung musste vor
 * den Compiler. Das Verzeichnis ist geblieben: Es hält sichtbar, was
 * {@code org.cef} überhaupt berührt.
 */
public final class CefHost {

    /** Der Client, an dem Browser und Handler hängen. */
    public static CefClient client() {
        return FnCefRuntime.client();
    }

    /** Die Anwendung, an der Schemata angemeldet werden. */
    public static CefApp app() {
        return FnCefRuntime.app();
    }

    /** Der Unterbau, so wie ihn {@code WebSupport} anfordert. */
    public static WebBackend backend() {
        return FnRuntimeBackend.create();
    }

    private CefHost() {
    }
}
