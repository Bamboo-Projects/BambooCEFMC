package dev.devpanda.bamboocef.web.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * All open surfaces in the world and the hook through which they are drawn.
 *
 * <p><b>Internal, not a guarantee</b> — like {@link Overlays}. Public only
 * because the client entry point has to draw, tick, and close.
 *
 * <p>The camera comes as three numbers, not as a {@code Vec3}: that type lives
 * in {@code net.minecraft.world}, and nothing from there may come in here.
 */
public final class WorldSurfaces {

    private static final Logger LOG = LogUtils.getLogger();

    private static final List<WorldSurfaceImpl> surfaces = new ArrayList<>();

    private WorldSurfaces() {
    }

    static void add(WorldSurfaceImpl surface) {
        surfaces.add(surface);
        LOG.info("{} geöffnet — offen: {}", surface, surfaces.size());
    }

    static void remove(WorldSurfaceImpl surface) {
        if (surfaces.remove(surface)) {
            LOG.info("{} geschlossen — offen: {}", surface, surfaces.size());
        }
    }

    /**
     * From the world render stage, after the translucent blocks.
     *
     * @param pose    the world renderer's stack, camera as the origin
     * @param buffers the world renderer's buffers
     */
    public static void draw(PoseStack pose, MultiBufferSource.BufferSource buffers,
                            double cameraX, double cameraY, double cameraZ) {
        if (surfaces.isEmpty()) {
            return;
        }
        for (WorldSurfaceImpl surface : List.copyOf(surfaces)) {
            surface.draw(pose, buffers, cameraX, cameraY, cameraZ);
        }
    }

    /** Each tick: whatever has lost its browser is cleaned up. */
    public static void tick() {
        if (surfaces.isEmpty()) {
            return;
        }
        for (WorldSurfaceImpl surface : List.copyOf(surfaces)) {
            if (!surface.alive()) {
                surface.close();
            }
        }
    }

    public static void closeAll() {
        for (WorldSurfaceImpl surface : List.copyOf(surfaces)) {
            surface.close();
        }
    }

    public static int count() {
        return surfaces.size();
    }

    /**
     * Which surface a ray hits first, and where.
     *
     * <p>The camera as three numbers, the view direction as three — no world
     * type. The nearest one within {@code maxDistance} blocks is hit.
     *
     * @return the hit or {@code null}
     */
    public static Pick pick(double camX, double camY, double camZ,
                            double dirX, double dirY, double dirZ, double maxDistance) {
        Pick best = null;
        for (WorldSurfaceImpl surface : surfaces) {
            double[] hit = surface.pick(camX, camY, camZ, dirX, dirY, dirZ);
            if (hit == null || hit[2] > maxDistance) {
                continue;
            }
            if (best == null || hit[2] < best.distance()) {
                best = new Pick(surface.surface(), (int) Math.round(hit[0]),
                        (int) Math.round(hit[1]), hit[2]);
            }
        }
        return best;
    }

    /** A hit: which surface, which pixel, how far. */
    public record Pick(WebSurface surface, int x, int y, double distance) {
    }
}
