package dev.devpanda.bamboocef.web;

/**
 * Was die Runtime von ihrem Unterbau braucht.
 *
 * <p><b>Eine Schnittstelle für einen Unterbau, den es nur einmal gibt</b> —
 * das sieht nach Vorrat aus und ist keiner. Sie ist die Stelle, an der die
 * Runtime geladen werden kann, <i>ohne dass Chromium da sein muss</i>. Stünde
 * hier ein Typ aus {@code org.cef}, brächte schon das Laden von
 * {@link WebRuntime} einen {@code NoClassDefFoundError} — und die Mod startete
 * nicht mehr, bloß weil eine Laufzeitumgebung fehlt, die 379 Megabyte wiegt
 * und getrennt ausgeliefert wird.
 *
 * <p>Das galt zur MCEF-Zeit, als der Unterbau eine fremde Mod war, und es gilt
 * seitdem umso mehr: Jetzt gehört er uns, aber er liegt immer noch nicht im
 * Jar.
 *
 * <p>Dass sie einen zweiten Unterbau tragen kann — einen GPU-Pfad etwa —, ist
 * ein Nebeneffekt und nicht der Zweck.
 */
public interface WebBackend extends AutoCloseable {

    /** Der Name für Protokollzeilen. */
    String name();

    /** Fährt herunter und gibt alles frei. Darf mehrfach gerufen werden. */
    @Override
    void close();
}
