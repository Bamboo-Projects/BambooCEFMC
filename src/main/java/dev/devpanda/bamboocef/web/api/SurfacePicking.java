package dev.devpanda.bamboocef.web.api;

import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where on a surface a ray points.
 *
 * <p>Pure geometry, without Chromium and without the world: from the center,
 * the orientation, and the size of a surface and a ray (origin and direction)
 * comes the pixel that is hit. Exactly the computation that, in the game,
 * decides whether a click lands where you are looking.
 *
 * <p><b>The same chain as when drawing, only backward.</b> {@link WorldSurfaceImpl}
 * rotates the surface with {@code YP(-yaw)} and {@code XP(pitch)}; here the
 * same matrix is built and inverted, so that the world point falls into the
 * surface's local measure. Local {@code +Z} points toward the viewer — the
 * front is the side the ray comes from.
 */
final class SurfacePicking {

    private SurfacePicking() {
    }

    /**
     * @return {@code {pixelX, pixelY, distance}} or {@code null} if the ray
     *         does not hit the front of the surface within its bounds
     */
    static double[] hit(double centerX, double centerY, double centerZ,
                        float yaw, float pitch, float widthBlocks, float heightBlocks,
                        int pixelWidth, int pixelHeight,
                        double originX, double originY, double originZ,
                        double dirX, double dirY, double dirZ) {
        Matrix4f forward = new Matrix4f()
                .translate((float) centerX, (float) centerY, (float) centerZ)
                .rotate(Axis.YP.rotationDegrees(-yaw))
                .rotate(Axis.XP.rotationDegrees(pitch));
        Matrix4f inverse = new Matrix4f(forward).invert();

        Vector3f origin = inverse.transformPosition(
                new Vector3f((float) originX, (float) originY, (float) originZ));
        // A direction, not a location: without the translation, hence transformDirection.
        Vector3f dir = inverse.transformDirection(
                new Vector3f((float) dirX, (float) dirY, (float) dirZ));

        // The plane is locally z = 0. A ray parallel to it never hits.
        if (Math.abs(dir.z) < 1e-7f) {
            return null;
        }
        float t = -origin.z / dir.z;
        if (t <= 0) {
            return null;      // behind the viewer
        }
        // The front is the one the ray comes from: locally the origin must
        // lie in front of the plane (z > 0).
        if (origin.z <= 0) {
            return null;
        }

        float localX = origin.x + t * dir.x;
        float localY = origin.y + t * dir.y;
        float halfW = widthBlocks / 2f;
        float halfH = heightBlocks / 2f;
        if (localX < -halfW || localX > halfW || localY < -halfH || localY > halfH) {
            return null;
        }

        // u from left (0) to right (1), v from top (0) to bottom (1) —
        // the way the texture lies.
        double u = (localX + halfW) / widthBlocks;
        double v = (halfH - localY) / heightBlocks;
        double distance = t * Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        return new double[] {u * pixelWidth, v * pixelHeight, distance};
    }
}
