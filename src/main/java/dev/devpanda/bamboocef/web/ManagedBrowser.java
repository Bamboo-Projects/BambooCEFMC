package dev.devpanda.bamboocef.web;

/**
 * Was der {@link BrowserManager} von einer Sitzung wissen muss.
 *
 * <p><b>Eine Schnittstelle für genau eine Umsetzung</b> — das sieht nach
 * Vorrat aus und ist keiner. Sie steht hier, damit {@code BrowserManager} in
 * {@code web} liegen kann, ohne {@code web.runtime} zu kennen: Dort hängt
 * Chromium dran, und wer den Verwalter lädt, soll es nicht mitladen müssen.
 */
public interface ManagedBrowser {

    /**
     * Schließt die Sitzung. Muss mehrfach gerufen werden dürfen.
     *
     * <p>Die Rückmeldung von Chromium — {@code onBeforeClose} — kommt danach
     * und asynchron. Wer wissen will, ob sie da ist, fragt den Verwalter.
     */
    void close();

    /** Wie die Sitzung im Protokoll heißen soll. */
    String describe();
}
