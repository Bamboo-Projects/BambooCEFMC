package dev.devpanda.bamboocef.web.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.devpanda.bamboocef.web.BrowserVisibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Eine Fläche in der Welt: eine {@link SessionSurface} und ihre Lage.
 *
 * <p>Gezeichnet wird ein Rechteck mit der Textur der Sitzung, beide Seiten,
 * voll beleuchtet — eine Seite leuchtet selbst, sie braucht kein Licht aus
 * der Welt. Die Geometrie ist die des Tafel-Renderers: erst zum Mittelpunkt,
 * dann um die Hochachse gegen den Gierwinkel drehen, dann das Rechteck um
 * den Ursprung. Lokales +Z zeigt danach in Blickrichtung der Fläche.
 */
final class WorldSurfaceImpl implements WorldSurface {

    /** Bis hierhin, in Blöcken, malt die Seite mit mittlerem Takt. */
    private static final double NEARBY_RANGE = 12.0;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final SessionSurface surface;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float width;
    private float height;
    private double maxDistance = 32.0;
    private boolean visible = true;
    private boolean closed;
    private BrowserVisibility applied;

    WorldSurfaceImpl(SessionSurface surface, double x, double y, double z,
                     float yaw, float width, float height) {
        this.surface = surface;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.width = Math.max(0.01F, width);
        this.height = Math.max(0.01F, height);
    }

    // ---- Lage -----------------------------------------------------------------

    @Override
    public WebSurface surface() {
        return surface;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public void rotate(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public float width() {
        return width;
    }

    @Override
    public float height() {
        return height;
    }

    @Override
    public void resize(float width, float height) {
        this.width = Math.max(0.01F, width);
        this.height = Math.max(0.01F, height);
    }

    @Override
    public double maxDistance() {
        return maxDistance;
    }

    @Override
    public void maxDistance(double blocks) {
        this.maxDistance = Math.max(1.0, blocks);
    }

    @Override
    public boolean visible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            apply(BrowserVisibility.HIDDEN);
        }
    }

    // ---- Zeichnen ---------------------------------------------------------------

    /**
     * Ein Bild, mit der Kamera als Ursprung.
     *
     * <p>Die Entfernung entscheidet über den Takt der Seite, bevor über das
     * Zeichnen entschieden wird: Eine ferne Seite ruht, ob sie nun im Bild
     * wäre oder nicht.
     */
    void draw(PoseStack pose, MultiBufferSource.BufferSource buffers,
              double cameraX, double cameraY, double cameraZ) {
        if (!alive()) {
            return;
        }
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!visible) {
            return;
        }
        apply(distance <= NEARBY_RANGE ? BrowserVisibility.NEARBY
                : distance <= maxDistance ? BrowserVisibility.DISTANT
                : BrowserVisibility.HIDDEN);
        if (distance > maxDistance) {
            return;
        }
        int textureId = surface.textureId();
        if (textureId == 0) {
            return;
        }

        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        Matrix4f matrix = pose.last().pose();
        RenderType type = RenderType.text(surface.textureLocation());
        VertexConsumer quad = buffers.getBuffer(type);
        float hw = width / 2f;
        float hh = height / 2f;
        // Vorderseite, gegen den Uhrzeigersinn von vorn gesehen.
        vertex(quad, matrix, -hw, -hh, 0f, 1f);
        vertex(quad, matrix, hw, -hh, 1f, 1f);
        vertex(quad, matrix, hw, hh, 1f, 0f);
        vertex(quad, matrix, -hw, hh, 0f, 0f);
        // Rückseite: dieselben Ecken andersherum — von hinten das Spiegelbild.
        vertex(quad, matrix, -hw, hh, 0f, 0f);
        vertex(quad, matrix, hw, hh, 1f, 0f);
        vertex(quad, matrix, hw, -hh, 1f, 1f);
        vertex(quad, matrix, -hw, -hh, 0f, 1f);
        pose.popPose();
        // Selbst abschließen: Nichts garantiert, dass der Weltrenderer
        // unseren Typ in dieser Stufe noch leert.
        buffers.endBatch(type);
    }

    private static void vertex(VertexConsumer quad, Matrix4f matrix,
                               float x, float y, float u, float v) {
        quad.addVertex(matrix, x, y, 0f)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setLight(FULL_BRIGHT);
    }

    private void apply(BrowserVisibility wanted) {
        if (wanted != applied && !closed) {
            applied = wanted;
            surface.session().setVisibility(wanted);
        }
    }

    // ---- Ende -------------------------------------------------------------------

    /**
     * Wohin ein Strahl auf dieser Fläche zeigt.
     *
     * @return {@code {pixelX, pixelY, entfernung}} oder {@code null}, wenn er
     *         sie nicht trifft; nur eine sichtbare, lebende Fläche trifft
     */
    double[] pick(double ox, double oy, double oz, double dx, double dy, double dz) {
        if (!visible || !alive()) {
            return null;
        }
        return SurfacePicking.hit(x, y, z, yaw, pitch, width, height,
                surface.width(), surface.height(), ox, oy, oz, dx, dy, dz);
    }

    @Override
    public boolean alive() {
        return !closed && surface.alive();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        WorldSurfaces.remove(this);
        surface.close();
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Weltfläche %s %.1fx%.1f bei %.1f,%.1f,%.1f (%.0f°)",
                surface.name(), width, height, x, y, z, yaw);
    }
}
