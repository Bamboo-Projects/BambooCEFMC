package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wohin auf der Seite das Fadenkreuz zeigt.
 *
 * <p><b>Der heikle Teil, für sich geprüft.</b> Ein Strahl von der Kamera
 * trifft die Fläche irgendwo; daraus muss ein Pixel werden, und zwar dasselbe,
 * das der Renderer an diese Stelle malt. Die Drehung von Hand nachzurechnen
 * ist genau die Art Fehler, die im Spiel als „ich klicke hier, es reagiert
 * dort" auffällt — also hier, ohne Spiel.
 *
 * <p>Eine Fläche mit Gierwinkel 0 schaut nach Süden, also in Welt-{@code +Z};
 * lesbar ist sie von dort, wo {@code +Z} größer wird. Der Strahl kommt also
 * von {@code +Z} und zielt nach {@code -Z}.
 */
class SurfacePickingTest {

    private static final int PW = 512;
    private static final int PH = 512;

    /** Eine Fläche bei (0,0,0), Gierwinkel 0, ein Block im Quadrat. */
    private static double[] hitAtOrigin(double ox, double oy, double oz,
                                        double dx, double dy, double dz) {
        return SurfacePicking.hit(0, 0, 0, 0f, 0f, 1f, 1f, PW, PH,
                ox, oy, oz, dx, dy, dz);
    }

    @Test
    @DisplayName("Geradeaus in die Mitte trifft die Mitte")
    void straightAheadHitsTheCenter() {
        double[] hit = hitAtOrigin(0, 0, 3, 0, 0, -1);

        assertNotNull(hit);
        assertEquals(PW / 2, Math.round(hit[0]));
        assertEquals(PH / 2, Math.round(hit[1]));
        assertEquals(3.0, hit[2], 1e-6, "die Entfernung ist der Weg bis zur Fläche");
    }

    @Test
    @DisplayName("Ein Ziel links der Mitte ergibt ein kleineres x")
    void aimingLeftGivesSmallerX() {
        // Von vorn gesehen ist links die kleinere Welt-x. Der Strahl zielt
        // leicht nach −x.
        double[] hit = hitAtOrigin(0, 0, 3, -0.1, 0, -1);

        assertNotNull(hit);
        assertTrue(hit[0] < PW / 2, "links der Mitte: x kleiner als die Hälfte, war " + hit[0]);
    }

    @Test
    @DisplayName("Ein Ziel über der Mitte ergibt ein kleineres y")
    void aimingUpGivesSmallerY() {
        // Oben ist v=0; der Strahl zielt leicht nach oben (+y).
        double[] hit = hitAtOrigin(0, 0, 3, 0, 0.1, -1);

        assertNotNull(hit);
        assertTrue(hit[1] < PH / 2, "über der Mitte: y kleiner als die Hälfte, war " + hit[1]);
    }

    @Test
    @DisplayName("Ein Strahl neben die Fläche trifft nichts")
    void aimingBesideTheSurfaceMisses() {
        // Weit nach rechts, über die halbe Blockbreite hinaus.
        assertNull(hitAtOrigin(0, 0, 3, 5, 0, -1));
    }

    @Test
    @DisplayName("Ein Strahl von hinten trifft nichts")
    void aimingFromBehindMisses() {
        // Hinter der Fläche, nach vorn zielend: kein Treffer der Vorderseite.
        assertNull(hitAtOrigin(0, 0, -3, 0, 0, -1));
    }

    @Test
    @DisplayName("Ein gedrehtes Display trifft dort, wo der Renderer malt")
    void aRotatedSurfaceIsConsistent() {
        // Fläche mit Gierwinkel 90 (schaut nach Westen, Welt −x). Ein Strahl
        // aus −x nach +x, mittig, muss die Mitte treffen.
        double[] hit = SurfacePicking.hit(0, 0, 0, 90f, 0f, 1f, 1f, PW, PH,
                -3, 0, 0, 1, 0, 0);

        assertNotNull(hit);
        assertEquals(PW / 2, Math.round(hit[0]), 1);
        assertEquals(PH / 2, Math.round(hit[1]), 1);
    }

    @Test
    @DisplayName("Ein paralleler Strahl trifft die Ebene nie")
    void aParallelRayNeverMeetsThePlane() {
        assertNull(hitAtOrigin(0, 0, 3, 1, 0, 0));
    }
}
