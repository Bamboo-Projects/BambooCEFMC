package dev.devpanda.bamboocef.web.api;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Alle offenen Flächen in der Welt und der Haken, über den sie gezeichnet werden.
 *
 * <p><b>Innen, kein Versprechen</b> — wie {@link Overlays}. Öffentlich nur,
 * weil der Client-Einstieg zeichnen, takten und schließen muss.
 *
 * <p>Die Kamera kommt als drei Zahlen, nicht als {@code Vec3}: Der Typ liegt
 * in {@code net.minecraft.world}, und das darf hier nichts herein.
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
     * Aus der Weltstufe nach den durchscheinenden Blöcken.
     *
     * @param pose    der Stapel des Weltrenderers, Kamera als Ursprung
     * @param buffers die Puffer des Weltrenderers
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

    /** Je Takt: Was seinen Browser verloren hat, wird abgeräumt. */
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
     * Welche Fläche ein Strahl zuerst trifft, und wo.
     *
     * <p>Die Kamera als drei Zahlen, die Blickrichtung als drei — kein
     * Weltklasse. Getroffen wird die nächste innerhalb von {@code maxDistance}
     * Blöcken.
     *
     * @return der Treffer oder {@code null}
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

    /** Ein Treffer: welche Fläche, welcher Pixel, wie weit. */
    public record Pick(WebSurface surface, int x, int y, double distance) {
    }
}
