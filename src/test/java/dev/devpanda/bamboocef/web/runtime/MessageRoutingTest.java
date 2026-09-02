package dev.devpanda.bamboocef.web.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which session receives a message from the page.
 *
 * <p><b>Why this is testable in isolation.</b> On a request from the page,
 * Chromium passes along the browser that made it — and several browsers share
 * one client. The one message must therefore reach the right session, and
 * that is a mapping over identities that needs no Chromium to be
 * tested.
 */
class MessageRoutingTest {

    @Test
    @DisplayName("A message goes to the session that carries its key")
    void aMessageReachesItsOwner() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);

        assertTrue(routing.dispatch(browser, "hallo"));
        assertEquals(List.of("hallo"), got);
    }

    @Test
    @DisplayName("An unknown browser reaches nobody")
    void anUnknownBrowserReachesNobody() {
        MessageRouting routing = new MessageRouting();

        assertFalse(routing.dispatch(new Object(), "hallo"),
                "ohne Empfänger ist die Nachricht nicht angenommen");
    }

    @Test
    @DisplayName("Two browsers keep their own receivers")
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
    @DisplayName("After unregistering the browser receives nothing more")
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
    @DisplayName("Equality does not count, only the same identity")
    void identityNotEquality() {
        // Two browsers can look alike and are still two. The
        // mapping hangs on the identity itself, not on equals.
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
    @DisplayName("An empty message goes through, a missing one does not")
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
    @DisplayName("Forgetting everything releases every receiver")
    void clearForgetsEveryone() {
        MessageRouting routing = new MessageRouting();
        List<String> got = new ArrayList<>();
        Object browser = new Object();
        routing.register(browser, got::add);
        routing.clear();

        assertFalse(routing.dispatch(browser, "hallo"));
    }
}
