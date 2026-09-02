package dev.devpanda.bamboocef.web.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Welche Sitzung eine Nachricht der Seite bekommt.
 *
 * <p><b>Warum das getrennt prüfbar ist.</b> Chromium reicht bei einer Anfrage
 * aus der Seite den Browser mit, der sie stellte — und mehrere Browser teilen
 * sich einen Client. Die eine Nachricht muss also zur richtigen Sitzung, und
 * das ist eine Zuordnung über Kennungen, die kein Chromium braucht, um
 * geprüft zu werden.
 */
class MessageRoutingTest {

    @Test
    @DisplayName("Eine Nachricht geht an die Sitzung, die ihren Schlüssel trägt")
    void aMessageReachesItsOwner() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);

        assertTrue(routing.dispatch(browser, "hallo"));
        assertEquals(List.of("hallo"), got);
    }

    @Test
    @DisplayName("Ein unbekannter Browser bekommt niemanden")
    void anUnknownBrowserReachesNobody() {
        MessageRouting routing = new MessageRouting();

        assertFalse(routing.dispatch(new Object(), "hallo"),
                "ohne Empfänger ist die Nachricht nicht angenommen");
    }

    @Test
    @DisplayName("Zwei Browser haben ihre eigenen Empfänger")
    void twoBrowsersKeepTheirOwn() {
        MessageRouting routing = new MessageRouting();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        Object browserA = new Object();
        Object browserB = new Object();
        routing.register(browserA, a::add);
        routing.register(browserB, b::add);

        routing.dispatch(browserA, "an A");
        routing.dispatch(browserB, "an B");

        assertEquals(List.of("an A"), a);
        assertEquals(List.of("an B"), b);
    }

    @Test
    @DisplayName("Nach dem Abmelden bekommt der Browser nichts mehr")
    void afterUnregisterNothingArrives() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);
        routing.unregister(browser);

        assertFalse(routing.dispatch(browser, "hallo"));
        assertTrue(got.isEmpty());
    }

    @Test
    @DisplayName("Gleichheit zählt nicht, nur dieselbe Kennung")
    void identityNotEquality() {
        // Zwei Browser können gleich aussehen und sind doch zwei. Die
        // Zuordnung hängt an der Kennung selbst, nicht an equals.
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        String key = new String("browser");
        String twin = new String("browser");
        routing.register(key, got::add);

        assertFalse(routing.dispatch(twin, "hallo"),
                "ein gleich benannter, aber anderer Browser ist nicht derselbe");
        assertTrue(routing.dispatch(key, "hallo"));
    }

    @Test
    @DisplayName("Eine leere Nachricht geht durch, eine fehlende nicht")
    void emptyPassesNullDoesNot() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);

        assertTrue(routing.dispatch(browser, ""));
        assertFalse(routing.dispatch(browser, null),
                "ohne Inhalt gibt es nichts weiterzureichen");
        assertEquals(List.of(""), got);
    }

    @Test
    @DisplayName("Alles vergessen macht jeden Empfänger los")
    void clearForgetsEveryone() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);
        routing.clear();

        assertFalse(routing.dispatch(browser, "hallo"));
    }
}
