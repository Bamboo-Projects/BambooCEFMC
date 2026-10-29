package dev.devpanda.bamboocef.web.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.devpanda.bamboocef.web.BrowserVisibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;

/**
 * A surface in the world: a {@link SessionSurface} and its placement.
 *
 * <p>A rectangle is drawn with the session's texture, both faces, fully lit —
 * a page glows on its own, it needs no light from the world. The geometry is
 * that of the board renderer: first to the center, then rotate about the
 * vertical axis against the yaw, then the rectangle about the origin. Local
 * +Z then points in the surface's viewing direction.
 */
final class WorldSurfaceImpl implements WorldSurface {

    /** Up to here, in blocks, the page draws at a medium cadence. */
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

    // ---- Position -------------------------------------------------------------

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

    // ---- Drawing ----------------------------------------------------------------

    /**
     * One frame, with the camera as the origin.
     *
     * <p>The distance decides the page's cadence before the drawing is
     * decided: a distant page rests, whether it would be in view or not.
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
        RenderType type = RenderTypes.text(surface.textureLocation());
        VertexConsumer quad = buffers.getBuffer(type);
        float hw = width / 2f;
        float hh = height / 2f;
        // Front face, counter-clockwise as seen from the front.
        vertex(quad, matrix, -hw, -hh, 0f, 1f);
        vertex(quad, matrix, hw, -hh, 1f, 1f);
        vertex(quad, matrix, hw, hh, 1f, 0f);
        vertex(quad, matrix, -hw, hh, 0f, 0f);
        // Back face: the same corners the other way around — the mirror image from behind.
        vertex(quad, matrix, -hw, hh, 0f, 0f);
        vertex(quad, matrix, hw, hh, 1f, 0f);
        vertex(quad, matrix, hw, -hh, 1f, 1f);
        vertex(quad, matrix, -hw, -hh, 0f, 1f);
        pose.popPose();
        // Flush it ourselves: nothing guarantees that the world renderer
        // still flushes our type at this stage.
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

    // ---- End --------------------------------------------------------------------

    /**
     * Where a ray points on this surface.
     *
     * @return {@code {pixelX, pixelY, distance}} or {@code null} if it does
     *         not hit; only a visible, living surface is hit
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
