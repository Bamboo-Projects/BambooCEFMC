package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Die eine Stelle, die alle offenen Browser kennt.
 *
 * <p><b>Warum es sie braucht.</b> Bisher wusste jeder Bildschirm von seinem
 * eigenen Browser und sonst niemand von irgendeinem. Beim Beenden des Spiels
 * hieß das: Was gerade offen war, schloss sich, wenn sein Bildschirm es tat —
 * und was keinen Bildschirm mehr hatte, blieb stehen, bis Chromium selbst
 * aufräumte. Für ein geordnetes Herunterfahren muss jemand die Liste haben.
 *
 * <p><b>Zwei Listen, nicht eine.</b> Eine Sitzung ist nicht schon zu, wenn
 * jemand {@code close()} gerufen hat: {@code CefBrowser.close(true)} ist eine
 * Bitte, und die Bestätigung — {@code onBeforeClose} — kommt später und aus
 * Chromiums Nachrichtenschleife. Wer nur eine Liste führt, hält beim
 * Herunterfahren entweder zu früh für fertig oder wartet auf etwas, das er
 * gerade selbst weggeworfen hat. Deshalb wandert eine Sitzung von
 * {@code offen} nach {@code wartet} und verschwindet erst mit der
 * Bestätigung.
 *
 * <p><b>Der {@code CefClient} gehört nicht hierher.</b> Er gehört der Runtime
 * und wird von allen Browsern geteilt; ihn je Browser freizugeben nähme dem
 * nächsten den Boden weg.
 *
 * <p><b>Thread:</b> Alles hier läuft im Renderthread — dort entstehen die
 * Sitzungen, dort wird gepumpt, dort kommen die Bestätigungen an. Die
 * Sammlungen sind trotzdem synchronisiert, weil {@code onBeforeClose} bei
 * einem Fehler auch aus einem fremden Thread kommen könnte und ein Verwalter,
 * der dabei die Liste zerreißt, den Fehler verdoppelte.
 */
public final class BrowserManager {

    private static final Logger LOG = LogUtils.getLogger();

    /** Was offen ist. */
    private static final Set<ManagedBrowser> open =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /** Was geschlossen wurde und noch auf Chromiums Bestätigung wartet. */
    private static final Set<ManagedBrowser> awaiting =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Eine Sitzung ist entstanden.
     *
     * <p>Zu rufen, sobald der Browser da ist — nicht davor: Ein Browser, der
     * beim Erzeugen scheitert, soll nicht als offen gelten.
     */
    public static void register(ManagedBrowser session) {
        open.add(session);
        LOG.info("Browser registriert: {} — offen: {}", session.describe(), count());
    }

    /**
     * Eine Sitzung will zugehen.
     *
     * <p>Sie gilt ab jetzt nicht mehr als offen, ist aber noch nicht fertig:
     * Chromium bestätigt das Schließen erst später.
     */
    public static void closing(ManagedBrowser session) {
        if (open.remove(session)) {
            awaiting.add(session);
            LOG.info("Browser schließt: {} — offen: {}, warten auf Bestätigung: {}",
                    session.describe(), count(), pending());
        }
    }

    /**
     * Chromium hat das Schließen bestätigt.
     *
     * <p>Das ist {@code onBeforeClose}, und erst damit ist die Sitzung
     * wirklich weg.
     */
    public static void closed(ManagedBrowser session) {
        boolean waited = awaiting.remove(session);
        boolean wasOpen = open.remove(session);
        if (waited || wasOpen) {
            LOG.info("Browser entfernt: {} — offen: {}, warten auf Bestätigung: {}",
                    session.describe(), count(), pending());
        }
    }

    /**
     * Nimmt eine Sitzung aus beiden Listen, ohne auf etwas zu warten.
     *
     * <p>Für den Fall, dass eine Sitzung auf einem anderen Weg verschwindet
     * als über {@code close()} — etwa weil ihre Erzeugung scheiterte.
     */
    public static void unregister(ManagedBrowser session) {
        boolean removed = open.remove(session) | awaiting.remove(session);
        if (removed) {
            LOG.info("Browser abgemeldet: {} — offen: {}", session.describe(), count());
        }
    }

    /** Wie viele Sitzungen offen sind. */
    public static int count() {
        return open.size();
    }

    /** Wie viele auf Chromiums Bestätigung warten. */
    public static int pending() {
        return awaiting.size();
    }

    /** Was gerade offen ist und was wartet — für Messungen und Fehlersuche. */
    public static List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        synchronized (open) {
            for (ManagedBrowser session : open) {
                lines.add("offen: " + session.describe());
            }
        }
        synchronized (awaiting) {
            for (ManagedBrowser session : awaiting) {
                lines.add("wartet: " + session.describe());
            }
        }
        return lines;
    }

    /**
     * Schließt alles, was offen ist.
     *
     * <p><b>Über eine Kopie.</b> Jedes {@code close()} meldet die Sitzung
     * mitten in der Schleife ab; wer über die lebende Sammlung liefe, bekäme
     * eine {@code ConcurrentModificationException} — und zwar erst dann, wenn
     * es beim Herunterfahren am wenigsten hilft.
     *
     * <p>Mehrfach zu rufen ist erlaubt und tut beim zweiten Mal nichts.
     */
    public static void closeAll() {
        List<ManagedBrowser> victims;
        synchronized (open) {
            victims = List.copyOf(open);
        }
        if (victims.isEmpty()) {
            return;
        }
        LOG.info("closeAll: {} offene Sitzungen", victims.size());
        for (ManagedBrowser session : victims) {
            try {
                session.close();
            } catch (Throwable broken) {
                // Eine Sitzung, die beim Schließen zickt, darf die anderen
                // nicht aufhalten — beim Beenden ist das die einzige Regel,
                // die zählt.
                LOG.warn("Beim Schließen von {} ging etwas schief",
                        session.describe(), broken);
                unregister(session);
            }
        }
    }

    /**
     * Wartet, bis Chromium alle Schließungen bestätigt hat.
     *
     * <p><b>Aus dem Thread zu rufen, der pumpt.</b> {@code onBeforeClose} kommt
     * aus Chromiums Nachrichtenschleife, und die dreht sich nur, solange
     * jemand sie dreht. Wer von woanders wartet, wartet auf ein Ereignis, das
     * allein er selbst auslösen könnte — und das ist kein Warten, sondern ein
     * Hängen.
     *
     * @param timeoutMillis wie lange höchstens
     * @param pump          eine Runde der Nachrichtenschleife
     * @return wahr, wenn alle bestätigt haben
     */
    public static boolean awaitClosed(long timeoutMillis, Runnable pump) {
        LOG.info("Warte auf {} Bestätigungen, höchstens {} ms — im Thread {}",
                pending(), timeoutMillis, Thread.currentThread().getName());
        long until = System.currentTimeMillis() + timeoutMillis;
        while (pending() > 0 && System.currentTimeMillis() < until) {
            try {
                pump.run();
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable broken) {
                LOG.warn("Beim Pumpen während des Schließens ging etwas schief", broken);
                break;
            }
        }
        if (pending() == 0) {
            LOG.info("Alle Schließungen bestätigt");
            return true;
        }
        // Die Frist ist abgelaufen. Wer fehlt, gehört ins Protokoll: Beim
        // nächsten Mal ist das der einzige Anhaltspunkt.
        LOG.warn("Frist abgelaufen — {} Sitzungen ohne Bestätigung: {}",
                pending(), snapshot());
        return false;
    }

    /** Vergisst alles. Nur für Prüfläufe. */
    static void forgetAll() {
        open.clear();
        awaiting.clear();
    }

    private BrowserManager() {
    }
}
