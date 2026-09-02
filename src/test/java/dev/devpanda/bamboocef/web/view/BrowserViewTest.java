package dev.devpanda.bamboocef.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one conversion that drawing and mouse both hang on.
 *
 * <p>The fault these test runs are meant to prevent does not look like a
 * calculation error in the game: the player clicks a button and hits the
 * one next to it. That gets reported as „the page reacts wrongly" and is long
 * searched for in the wrong place.
 */
class BrowserViewTest {

    @Test
    @DisplayName("At scale 1, GUI units and browser pixels are the same")
    void scaleOneChangesNothing() {
        BrowserView view = BrowserView.fullscreen(800, 600, 1.0);

        assertEquals(800, view.browserWidth());
        assertEquals(600, view.browserHeight());
        assertEquals(400, view.toBrowserX(400));
        assertEquals(300, view.toBrowserY(300));
    }

    @Test
    @DisplayName("At scale 3, the browser gets the full resolution")
    void scaleThreeGivesFullResolution() {
        // 640x360 GUI units on a 1920x1080 screen
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        assertEquals(1920, view.browserWidth(),
                "sonst rechnete der Editor auf einem Drittel der Auflösung");
        assertEquals(1080, view.browserHeight());
    }

    @Test
    @DisplayName("The center stays the center, no matter how it is scaled")
    void theMiddleStaysTheMiddle() {
        for (double scale : new double[] {1.0, 2.0, 3.0, 4.0}) {
            BrowserView view = BrowserView.fullscreen(640, 360, scale);

            assertEquals(view.browserWidth() / 2, view.toBrowserX(320),
                    "Skalierung " + scale);
            assertEquals(view.browserHeight() / 2, view.toBrowserY(180),
                    "Skalierung " + scale);
        }
    }

    @Test
    @DisplayName("A margin shifts both alike — picture and mouse")
    void anOffsetMovesPictureAndMouseAlike() {
        BrowserView view = new BrowserView(20, 10, 600, 340, 2.0);

        assertEquals(0, view.toBrowserX(20), "die linke Kante ist Browser-Null");
        assertEquals(0, view.toBrowserY(10));
        assertEquals(200, view.toBrowserX(120), "hundert GUI-Einheiten weiter sind zweihundert Pixel");
        assertArrayEquals(new double[] {20.0, 10.0}, view.toGui(0, 0));
        assertArrayEquals(new double[] {120.0, 10.0}, view.toGui(200, 0));
    }

    @Test
    @DisplayName("What lies outside is clamped and not extrapolated out")
    void outsideIsClampedNotExtrapolated() {
        BrowserView view = BrowserView.fullscreen(100, 100, 2.0);

        assertEquals(0, view.toBrowserX(-50),
                "eine Koordinate links des Fensters bräche ein laufendes Ziehen ab");
        assertEquals(199, view.toBrowserX(1000), "letzter gültiger Pixel, nicht 200");
        assertFalse(view.contains(-1, 50));
        assertFalse(view.contains(50, 100), "die untere Kante gehört nicht mehr dazu");
        assertTrue(view.contains(0, 0));
        assertTrue(view.contains(99.9, 99.9));
    }

    @Test
    @DisplayName("The path via the fraction lands in the same plane")
    void theFractionPathLandsInTheSamePlane() {
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        // A surface in the world reports „halfway across, a quarter
        // down". That must hit the same pixel as the mouse there.
        assertArrayEquals(new int[] {960, 270}, view.fromFraction(0.5, 0.25));
        assertEquals(view.toBrowserX(320), view.fromFraction(0.5, 0.25)[0]);
        assertEquals(view.toBrowserY(90), view.fromFraction(0.5, 0.25)[1]);
    }

    @Test
    @DisplayName("The fraction one hits the last pixel, not the first behind it")
    void fractionOneHitsTheLastPixel() {
        BrowserView view = BrowserView.fullscreen(100, 100, 1.0);

        assertArrayEquals(new int[] {99, 99}, view.fromFraction(1.0, 1.0));
        assertArrayEquals(new int[] {0, 0}, view.fromFraction(0.0, 0.0));
    }

    @Test
    @DisplayName("An aspect ratio is preserved because only one number scales")
    void theAspectRatioSurvives() {
        BrowserView view = BrowserView.fullscreen(1600, 900, 1.5);

        double guiAspect = 1600.0 / 900.0;
        double browserAspect = view.browserWidth() / (double) view.browserHeight();
        assertEquals(guiAspect, browserAspect, 0.001,
                "sonst wäre das Bild gestreckt");
    }

    @Test
    @DisplayName("Same browser size means: no message to Chromium needed")
    void sameSizeNeedsNoMessage() {
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        assertTrue(view.sameSizeAs(BrowserView.fullscreen(640, 360, 3.0)));
        assertTrue(view.sameSizeAs(new BrowserView(50, 50, 640, 360, 3.0)),
                "verschoben ist nicht anders groß");
        assertFalse(view.sameSizeAs(BrowserView.fullscreen(640, 360, 2.0)));
        assertFalse(view.sameSizeAs(null));
    }

    @Test
    @DisplayName("A surface without extent is rejected")
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new BrowserView(0, 0, 0, 100, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new BrowserView(0, 0, 100, 100, 0.0));
    }

    @Test
    @DisplayName("Even a tiny surface gets at least one pixel")
    void evenATinyAreaGetsOnePixel() {
        // When the window is dragged closed, the height passes through very small values.
        BrowserView view = new BrowserView(0, 0, 1, 1, 0.1);

        assertEquals(1, view.browserWidth(), "null Pixel breit malt nie wieder");
        assertEquals(1, view.browserHeight());
    }
}
