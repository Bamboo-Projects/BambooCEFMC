package dev.devpanda.bamboocef.web.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input logic, tested without Chromium and without waiting.
 *
 * <p>Everything here are faults that do not look like faults in the game: a
 * double click that selects no word. A right mouse button that inserts
 * text. A drag that breaks off after the first pixel. You search for them in
 * the web interface and do not find them, because they lie here.
 */
class InputLogicTest {

    @Nested
    @DisplayName("Counting clicks")
    class Clicks {

        @Test
        @DisplayName("Two clicks in quick succession at the same spot are a double click")
        void twoQuickClicksAreADouble() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(2, counter.pressed(0, 100, 100, 1200));
            assertEquals(3, counter.pressed(0, 100, 100, 1400));
        }

        @Test
        @DisplayName("After the third the count starts over")
        void afterTripleItStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 10, 10, 0);
            counter.pressed(0, 10, 10, 100);
            counter.pressed(0, 10, 10, 200);

            assertEquals(1, counter.pressed(0, 10, 10, 300),
                    "keine Oberfläche kennt einen Vierfachklick");
        }

        @Test
        @DisplayName("Too slow is not a double click")
        void tooSlowIsTwoSingles() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(1, counter.pressed(0, 100, 100, 1501));
        }

        @Test
        @DisplayName("Too far off is not a double click")
        void tooFarIsTwoSingles() {
            ClickCounter counter = new ClickCounter();

            assertEquals(1, counter.pressed(0, 100, 100, 1000));
            assertEquals(1, counter.pressed(0, 130, 100, 1100),
                    "zwei schnelle Klicks an verschiedene Stellen sind zwei Klicks");
        }

        @Test
        @DisplayName("One pixel of drift is allowed")
        void aPixelOfDriftIsFine() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 100, 100, 1000);

            assertEquals(2, counter.pressed(0, 102, 98, 1100),
                    "eine Maus wandert beim Doppelklicken immer ein wenig");
        }

        @Test
        @DisplayName("Another button starts over")
        void anotherButtonStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 100, 100, 1000);

            assertEquals(1, counter.pressed(1, 100, 100, 1100));
        }

        @Test
        @DisplayName("The release carries the same count as the press")
        void releaseCarriesTheSameCount() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 5, 5, 0);
            counter.pressed(0, 5, 5, 100);

            assertEquals(2, counter.released(),
                    "sonst zerfiele der Doppelklick auf halbem Weg");
        }

        @Test
        @DisplayName("Whoever leaves the surface starts over")
        void leavingTheAreaStartsOver() {
            ClickCounter counter = new ClickCounter();
            counter.pressed(0, 5, 5, 0);
            counter.forget();

            assertEquals(1, counter.pressed(0, 5, 5, 100));
            assertEquals(1, new ClickCounter().released(), "ohne Klick bleibt es bei eins");
        }
    }

    @Nested
    @DisplayName("Translating mouse buttons")
    class Buttons {

        @Test
        @DisplayName("Middle and right are swapped")
        void middleAndRightAreSwapped() {
            assertEquals(0, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_LEFT));
            assertEquals(1, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_MIDDLE),
                    "CEF zählt die mittlere als eins");
            assertEquals(2, MouseButtons.toBrowserButton(MouseButtons.MINECRAFT_RIGHT),
                    "sonst fügt die rechte Maustaste Text ein statt ein Menü zu öffnen");
        }

        @Test
        @DisplayName("A fourth mouse button is rejected instead of guessed")
        void unknownButtonsAreRefused() {
            assertEquals(-1, MouseButtons.toBrowserButton(3));
            assertEquals(0, MouseButtons.maskOf(3));
        }

        @Test
        @DisplayName("Pressed buttons sit in the same flags as Ctrl and Shift")
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
        @DisplayName("A released button does not stick in the drag")
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
    @DisplayName("Focus")
    class Focus {

        @Test
        @DisplayName("Exactly one gets the key, never both")
        void exactlyOneGetsTheKey() {
            for (BrowserFocus focus : BrowserFocus.values()) {
                assertTrue(focus.routesKeyboard() ^ focus.routesGameplay(),
                        focus + ": sonst öffnet ein getipptes e zusätzlich das Inventar");
            }
        }

        @Test
        @DisplayName("The browser gets the keyboard, Minecraft the game controls")
        void eachSideGetsItsOwn() {
            assertTrue(BrowserFocus.BROWSER.routesKeyboard());
            assertFalse(BrowserFocus.BROWSER.routesGameplay());
            assertTrue(BrowserFocus.MINECRAFT.routesGameplay());
            assertFalse(BrowserFocus.MINECRAFT.routesKeyboard());
        }
    }
}
