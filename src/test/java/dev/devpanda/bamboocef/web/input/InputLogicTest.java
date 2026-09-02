package dev.devpanda.bamboocef.web.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Eingabelogik, geprüft ohne Chromium und ohne Warten.
 *
 * <p>Alles hier sind Fehler, die im Spiel nicht wie Fehler aussehen: Ein
 * Doppelklick, der kein Wort markiert. Eine rechte Maustaste, die Text
 * einfügt. Ein Ziehen, das nach dem ersten Pixel abbricht. Man sucht sie in
 * der Weboberfläche und findet sie nicht, weil sie hier liegen.
 */
class InputLogicTest {

    @Nested
    @DisplayName("Klicks zählen")
    class Clicks {

        @Test
        @DisplayName("Zwei Klicks kurz hintereinander an derselben Stelle sind ein Doppelklick")
        void twoQuickClicksAreADouble() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(2, counter.pressed(0, 100, 100, 1200));
            assertEquals(3, counter.pressed(0, 100, 100, 1400));
        }

        @Test
        @DisplayName("Nach dem dritten beginnt die Zählung von vorn")
        void afterTripleItStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 10, 10, 0);
            counter.pressed(0, 10, 10, 100);
            counter.pressed(0, 10, 10, 200);

            assertEquals(1, counter.pressed(0, 10, 10, 300),
                    "keine Oberfläche kennt einen Vierfachklick");
        }

        @Test
        @DisplayName("Zu langsam ist kein Doppelklick")
        void tooSlowIsTwoSingles() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(1, counter.pressed(0, 100, 100, 1501));
        }

        @Test
        @DisplayName("Zu weit daneben ist kein Doppelklick")
        void tooFarIsTwoSingles() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(1, counter.pressed(0, 130, 100, 1100),
                    "zwei schnelle Klicks an verschiedene Stellen sind zwei Klicks");
        }

        @Test
        @DisplayName("Ein Pixel Wandern ist erlaubt")
        void aPixelOfDriftIsFine() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 100, 100, 1000);

            assertEquals(2, counter.pressed(0, 102, 98, 1100),
                    "eine Maus wandert beim Doppelklicken immer ein wenig");
        }

        @Test
        @DisplayName("Eine andere Taste beginnt neu")
        void anotherButtonStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 100, 100, 1000);

            assertEquals(1, counter.pressed(1, 100, 100, 1100));
        }

        @Test
        @DisplayName("Das Loslassen trägt dieselbe Zahl wie der Druck")
        void releaseCarriesTheSameCount() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 5, 5, 0);
            counter.pressed(0, 5, 5, 100);

            assertEquals(2, counter.released(),
                    "sonst zerfiele der Doppelklick auf halbem Weg");
        }

        @Test
        @DisplayName("Wer die Fläche verlässt, fängt neu an")
        void leavingTheAreaStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 5, 5, 0);
            counter.forget();

            assertEquals(1, counter.pressed(0, 5, 5, 100));
            assertEquals(1, new ClickCounter().released(), "ohne Klick bleibt es bei eins");
        }
    }

    @Nested
    @DisplayName("Maustasten umsetzen")
    class Buttons {

        @Test
        @DisplayName("Mitte und rechts sind vertauscht")
        void middleAndRightAreSwapped() {
            assertEquals(0, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_LEFT));
            assertEquals(1, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_MIDDLE),
                    "CEF zählt die mittlere als eins");
            assertEquals(2, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_RIGHT),
                    "sonst fügt die rechte Maustaste Text ein statt ein Menü zu öffnen");
        }

        @Test
        @DisplayName("Eine vierte Maustaste wird abgelehnt statt geraten")
        void unknownButtonsAreRefused() {
            assertEquals(-1, MouseButtons.toBrowserButton(3));
            assertEquals(0, MouseButtons.maskOf(3));
        }

        @Test
        @DisplayName("Gedrückte Tasten stehen in denselben Flaggen wie Strg und Umschalt")
        void pressedButtonsShareTheModifierField() {
            MouseButtons buttons = new MouseButtons();
            buttons.press(MouseButtons.MINECRAFT_LEFT);

            int shiftOnly = 0x0001;
            int combined = buttons.modifiersWith(shiftOnly);

            assertTrue((combined & MouseButtons.LEFT_MASK) != 0);
            assertTrue((combined & shiftOnly) != 0,
                    "sonst ginge die Umschalttaste beim Ziehen verloren");
        }

        @Test
        @DisplayName("Eine losgelassene Taste hängt nicht im Ziehen fest")
        void aReleasedButtonDoesNotStick() {
            MouseButtons buttons = new MouseButtons();
            buttons.press(MouseButtons.MINECRAFT_LEFT);
            buttons.press(MouseButtons.MINECRAFT_RIGHT);
            buttons.release(MouseButtons.MINECRAFT_LEFT);

            assertEquals(MouseButtons.RIGHT_MASK, buttons.modifiersWith(0));
            assertTrue(buttons.anyPressed());

            buttons.forget();
            assertFalse(buttons.anyPressed());
            assertEquals(0, buttons.modifiersWith(0));
        }
    }

    @Nested
    @DisplayName("Fokus")
    class Focus {

        @Test
        @DisplayName("Genau einer bekommt die Taste, nie beide")
        void exactlyOneGetsTheKey() {
            for (BrowserFocus focus : BrowserFocus.values()) {
                assertTrue(focus.routesKeyboard() ^ focus.routesGameplay(),
                        focus + ": sonst öffnet ein getipptes e zusätzlich das Inventar");
            }
        }

        @Test
        @DisplayName("Der Browser bekommt die Tastatur, Minecraft die Spielsteuerung")
        void eachSideGetsItsOwn() {
            assertTrue(BrowserFocus.BROWSER.routesKeyboard());
            assertFalse(BrowserFocus.BROWSER.routesGameplay());
            assertTrue(BrowserFocus.MINECRAFT.routesGameplay());
            assertFalse(BrowserFocus.MINECRAFT.routesKeyboard());
        }
    }
}
