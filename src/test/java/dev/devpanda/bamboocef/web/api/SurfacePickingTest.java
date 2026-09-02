package dev.devpanda.bamboocef.web.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where on the page the crosshair points.
 *
 * <p><b>The tricky part, tested on its own.</b> A ray from the camera
 * hits the surface somewhere; from that a pixel must come, namely the same one
 * that the renderer draws at this spot. Recomputing the rotation by hand
 * is exactly the kind of mistake that shows up in the game as „I click here, it reacts
 * there" — so here, without the game.
 *
 * <p>A surface with yaw 0 looks south, that is into world {@code +Z};
 * readable it is from where {@code +Z} grows larger. The ray therefore comes
 * from {@code +Z} and aims toward {@code -Z}.
 */
class SurfacePickingTest {

    private static final int PW = 512;
    private static final int PH = 512;

    /** A surface at (0,0,0), yaw 0, one block square. */
    private static double[] hitAtOrigin(double ox, double oy, double oz,
                                        double dx, double dy, double dz) {
        return SurfacePicking.hit(0, 0, 0, 0f, 0f, 1f, 1f, PW, PH,
                ox, oy, oz, dx, dy, dz);
    }

    @Test
    @DisplayName("Straight ahead into the center hits the center")
    void straightAheadHitsTheCenter() {
        double[] hit = hitAtOrigin(0, 0, 3, 0, 0, -1);

        assertNotNull(hit);
        assertEquals(PW / 2, Math.round(hit[0]));
        assertEquals(PH / 2, Math.round(hit[1]));
        assertEquals(3.0, hit[2], 1e-6, "die Entfernung ist der Weg bis zur Fläche");
    }

    @Test
    @DisplayName("A target left of center yields a smaller x")
    void aimingLeftGivesSmallerX() {
        // Seen from the front, the left is the smaller world x. The ray aims
        // slightly toward −x.
        double[] hit = hitAtOrigin(0, 0, 3, -0.1, 0, -1);

        assertNotNull(hit);
        assertTrue(hit[0] < PW / 2, "links der Mitte: x kleiner als die Hälfte, war " + hit[0]);
    }

    @Test
    @DisplayName("A target above center yields a smaller y")
    void aimingUpGivesSmallerY() {
        // Top is v=0; the ray aims slightly upward (+y).
        double[] hit = hitAtOrigin(0, 0, 3, 0, 0.1, -1);

        assertNotNull(hit);
        assertTrue(hit[1] < PH / 2, "über der Mitte: y kleiner als die Hälfte, war " + hit[1]);
    }

    @Test
    @DisplayName("A ray beside the surface hits nothing")
    void aimingBesideTheSurfaceMisses() {
        // Far to the right, beyond half the block width.
        assertNull(hitAtOrigin(0, 0, 3, 5, 0, -1));
    }

    @Test
    @DisplayName("A ray from behind hits nothing")
    void aimingFromBehindMisses() {
        // Behind the surface, aiming forward: no hit on the front side.
        assertNull(hitAtOrigin(0, 0, -3, 0, 0, -1));
    }

    @Test
    @DisplayName("A rotated display hits where the renderer draws")
    void aRotatedSurfaceIsConsistent() {
        // Surface with yaw 90 (looks west, world −x). A ray
        // from −x toward +x, centered, must hit the center.
        double[] hit = SurfacePicking.hit(0, 0, 0, 90f, 0f, 1f, 1f, PW, PH,
                -3, 0, 0, 1, 0, 0);

        assertNotNull(hit);
        assertEquals(PW / 2, Math.round(hit[0]), 1);
        assertEquals(PH / 2, Math.round(hit[1]), 1);
    }

    @Test
    @DisplayName("A parallel ray never meets the plane")
    void aParallelRayNeverMeetsThePlane() {
        assertNull(hitAtOrigin(0, 0, 3, 1, 0, 0));
    }
}
