package dev.devpanda.bamboocef.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die eine Umrechnung, an der Zeichnen und Maus gemeinsam hängen.
 *
 * <p>Der Fehler, den diese Prüfläufe verhindern sollen, sieht im Spiel nicht
 * aus wie ein Rechenfehler: Der Spieler klickt auf einen Knopf und trifft den
 * daneben. Das wird als „die Seite reagiert falsch" gemeldet und lange an der
 * falschen Stelle gesucht.
 */
class BrowserViewTest {

    @Test
    @DisplayName("Bei Skalierung 1 sind GUI-Einheiten und Browser-Pixel dasselbe")
    void scaleOneChangesNothing() {
        BrowserView view = BrowserView.fullscreen(800, 600, 1.0);

        assertEquals(800, view.browserWidth());
        assertEquals(600, view.browserHeight());
        assertEquals(400, view.toBrowserX(400));
        assertEquals(300, view.toBrowserY(300));
    }

    @Test
    @DisplayName("Bei Skalierung 3 bekommt der Browser die volle Auflösung")
    void scaleThreeGivesFullResolution() {
        // 640x360 GUI-Einheiten auf einem 1920x1080-Schirm
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        assertEquals(1920, view.browserWidth(),
                "sonst rechnete der Editor auf einem Drittel der Auflösung");
        assertEquals(1080, view.browserHeight());
    }

    @Test
    @DisplayName("Die Mitte bleibt die Mitte, egal wie skaliert wird")
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
    @DisplayName("Ein Rand verschiebt beides gleich — Bild und Maus")
    void anOffsetMovesPictureAndMouseAlike() {
        BrowserView view = new BrowserView(20, 10, 600, 340, 2.0);

        assertEquals(0, view.toBrowserX(20), "die linke Kante ist Browser-Null");
        assertEquals(0, view.toBrowserY(10));
        assertEquals(200, view.toBrowserX(120), "hundert GUI-Einheiten weiter sind zweihundert Pixel");
        assertArrayEquals(new double[] {20.0, 10.0}, view.toGui(0, 0));
        assertArrayEquals(new double[] {120.0, 10.0}, view.toGui(200, 0));
    }

    @Test
    @DisplayName("Was außerhalb liegt, wird geklemmt und nicht hinausgerechnet")
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
    @DisplayName("Der Weg über den Anteil landet in derselben Ebene")
    void theFractionPathLandsInTheSamePlane() {
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        // Eine Fläche in der Welt meldet „auf halber Strecke, ein Viertel
        // hinunter". Das muss dasselbe Pixel treffen wie die Maus dort.
        assertArrayEquals(new int[] {960, 270}, view.fromFraction(0.5, 0.25));
        assertEquals(view.toBrowserX(320), view.fromFraction(0.5, 0.25)[0]);
        assertEquals(view.toBrowserY(90), view.fromFraction(0.5, 0.25)[1]);
    }

    @Test
    @DisplayName("Der Anteil eins trifft den letzten Pixel, nicht den ersten dahinter")
    void fractionOneHitsTheLastPixel() {
        BrowserView view = BrowserView.fullscreen(100, 100, 1.0);

        assertArrayEquals(new int[] {99, 99}, view.fromFraction(1.0, 1.0));
        assertArrayEquals(new int[] {0, 0}, view.fromFraction(0.0, 0.0));
    }

    @Test
    @DisplayName("Ein Seitenverhältnis bleibt erhalten, weil nur eine Zahl skaliert")
    void theAspectRatioSurvives() {
        BrowserView view = BrowserView.fullscreen(1600, 900, 1.5);

        double guiAspect = 1600.0 / 900.0;
        double browserAspect = view.browserWidth() / (double) view.browserHeight();
        assertEquals(guiAspect, browserAspect, 0.001,
                "sonst wäre das Bild gestreckt");
    }

    @Test
    @DisplayName("Gleiche Browsergröße heißt: keine Meldung an Chromium nötig")
    void sameSizeNeedsNoMessage() {
        BrowserView view = BrowserView.fullscreen(640, 360, 3.0);

        assertTrue(view.sameSizeAs(BrowserView.fullscreen(640, 360, 3.0)));
        assertTrue(view.sameSizeAs(new BrowserView(50, 50, 640, 360, 3.0)),
                "verschoben ist nicht anders groß");
        assertFalse(view.sameSizeAs(BrowserView.fullscreen(640, 360, 2.0)));
        assertFalse(view.sameSizeAs(null));
    }

    @Test
    @DisplayName("Eine Fläche ohne Ausdehnung wird abgelehnt")
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new BrowserView(0, 0, 0, 100, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new BrowserView(0, 0, 100, 100, 0.0));
    }

    @Test
    @DisplayName("Selbst eine winzige Fläche bekommt mindestens einen Pixel")
    void evenATinyAreaGetsOnePixel() {
        // Beim Zuziehen des Fensters geht die Höhe durch sehr kleine Werte.
        BrowserView view = new BrowserView(0, 0, 1, 1, 0.1);

        assertEquals(1, view.browserWidth(), "null Pixel breit malt nie wieder");
        assertEquals(1, view.browserHeight());
    }
}
