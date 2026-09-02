package dev.devpanda.bamboocef.web.api;

/**
 * A surface in the world: hung on a location, an orientation, and a size.
 *
 * <p><b>Location and size in blocks, orientation in degrees.</b> The location
 * is the center of the surface in world coordinates. The yaw follows
 * Minecraft's convention (South 0, West 90, North 180, East 270): a surface
 * with {@code yaw = facing.toYRot()} looks in the same direction as a block
 * with that orientation — and is readable from there. From behind it shows
 * its mirror image, like a pane of glass.
 *
 * <p>The page's resolution comes from the {@link SurfaceSpec} in pixels; the
 * extent in blocks is independent of it. A surface two blocks wide with 512
 * pixels is sharp, one of twenty is not.
 *
 * <p><b>What the distance decides.</b> Up to twelve blocks the page draws at a
 * medium cadence, up to {@link #maxDistance()} slowly, beyond that not at
 * all — and it is not drawn either. That is decided by the distance to the
 * camera, not the view direction: a page behind the player keeps running, a
 * distant one stands still.
 *
 * <p>There is no input here; the surface beneath takes it by hand
 * ({@link #surface()}), for whoever wants to give it some.
 *
 * <p>Version 1. What is here stays.
 */
public interface WorldSurface extends AutoCloseable {

    WebSurface surface();

    double x();

    double y();

    double z();

    void moveTo(double x, double y, double z);

    /** Yaw in degrees, Minecraft's convention. */
    float yaw();

    /** Pitch in degrees; positive tilts the top edge backward. */
    float pitch();

    void rotate(float yaw, float pitch);

    /** Width in blocks. */
    float width();

    /** Height in blocks. */
    float height();

    void resize(float width, float height);

    /** From this distance to the camera, in blocks, the page rests. */
    double maxDistance();

    void maxDistance(double blocks);

    boolean visible();

    /** Invisible means: not drawn, and the page rests. */
    void setVisible(boolean visible);

    boolean alive();

    @Override
    void close();
}
