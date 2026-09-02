package dev.devpanda.bamboocef.web.api;

import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Wohin auf einer Fläche ein Strahl zeigt.
 *
 * <p>Reine Geometrie, ohne Chromium und ohne Welt: aus dem Mittelpunkt, der
 * Ausrichtung und der Größe einer Fläche und einem Strahl (Ursprung und
 * Richtung) wird der getroffene Pixel. Genau die Rechnung, die im Spiel
 * darüber entscheidet, ob ein Klick dort landet, wo man hinsieht.
 *
 * <p><b>Dieselbe Kette wie beim Zeichnen, nur rückwärts.</b> {@link WorldSurfaceImpl}
 * dreht die Fläche mit {@code YP(-yaw)} und {@code XP(pitch)}; hier wird
 * dieselbe Matrix gebaut und umgekehrt, damit der Weltpunkt in das lokale
 * Maß der Fläche fällt. Lokales {@code +Z} zeigt zum Betrachter — die
 * Vorderseite ist die Seite, von der der Strahl kommt.
 */
final class SurfacePicking {

    private SurfacePicking() {
    }

    /**
     * @return {@code {pixelX, pixelY, entfernung}} oder {@code null}, wenn der
     *         Strahl die Vorderseite der Fläche nicht innerhalb ihrer Ränder
     *         trifft
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
        // Eine Richtung, kein Ort: ohne die Verschiebung, deshalb transformDirection.
        Vector3f dir = inverse.transformDirection(
                new Vector3f((float) dirX, (float) dirY, (float) dirZ));

        // Die Ebene ist lokal z = 0. Ein Strahl parallel dazu trifft sie nie.
        if (Math.abs(dir.z) < 1e-7f) {
            return null;
        }
        float t = -origin.z / dir.z;
        if (t <= 0) {
            return null;      // hinter dem Betrachter
        }
        // Die Vorderseite ist die, von der der Strahl kommt: lokal muss der
        // Ursprung vor der Ebene liegen (z > 0).
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

        // u von links (0) nach rechts (1), v von oben (0) nach unten (1) —
        // wie die Textur liegt.
        double u = (localX + halfW) / widthBlocks;
        double v = (halfH - localY) / heightBlocks;
        double distance = t * Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        return new double[] {u * pixelWidth, v * pixelHeight, distance};
    }
}
