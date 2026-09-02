package dev.devpanda.bamboocef.web.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.bamboocef.web.frame.BorrowedFrame;
import dev.devpanda.bamboocef.web.frame.BrowserFrame;
import dev.devpanda.bamboocef.web.frame.DirtyRegion;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * Uploads browser images into an OpenGL texture.
 *
 * <p><b>Without {@code NativeImage} and without {@code DynamicTexture}.</b>
 * Both would have meant an extra copy in main memory: a {@code NativeImage}
 * owns its own buffer, and to get Chromium's image in one would have to write
 * it in there before it goes to the graphics card. The path here gives LWJGL
 * the borrowed buffer directly — one copy fewer per image, eight and a half
 * megabytes at 1080p.
 *
 * <p><b>The format is not negotiable.</b> Chromium delivers BGRA with the
 * origin at the top left. {@code GL_BGRA} together with
 * {@code GL_UNSIGNED_INT_8_8_8_8_REV} is the combination that takes this on
 * without reordering; any other costs either a shader or a loop over every
 * pixel.
 *
 * <p><b>On the top-left origin:</b> OpenGL textures count from the bottom. Even
 * so, nothing is flipped here — that would cost another copy. Whoever draws the
 * image flips the texture coordinates instead. Flipping on upload would be the
 * most expensive place for the cheapest computation.
 *
 * <p>All calls belong on the render thread. That is a given today: Chromium's
 * message loop is pumped there, so {@code onPaint} arrives there too.
 */
public final class GlTextureBackend implements BrowserTextureBackend {

    /** Four bytes per pixel. */
    private static final int BYTES_PER_PIXEL = 4;

    private int textureId;
    private int width;
    private int height;
    private boolean closed;

    /** How many bytes have been uploaded so far — for the measurement. */
    private long uploadedBytes;
    private long uploads;
    private long fullUploads;
    private long regions;
    private final DurationSamples uploadTimes = new DurationSamples();

    /**
     * Creates the texture.
     *
     * <p>Must run on the render thread. Before that there is no texture, and
     * {@link #textureId()} answers with zero.
     */
    public void initialize() {
        if (closed || textureId != 0) {
            return;
        }
        textureId = GL11.glGenTextures();
        RenderSystem.bindTexture(textureId);
        // Linear and not nearest neighbor: a web interface is rarely shown at
        // its own resolution, and hard edges on text look worse than a soft
        // transition.
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        // No repeat: at the edge of a surface there should be no second one.
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.bindTexture(0);
    }

    /**
     * Uploads a borrowed image — the cheapest way there is.
     *
     * <p>The buffer belongs to Chromium and is valid only during this call.
     * That is exactly why {@link BorrowedFrame} is here and not
     * {@link BrowserFrame}: what is only borrowed should be named accordingly.
     */
    public void upload(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        initialize();
        if (textureId == 0) {
            return;
        }
        long started = System.nanoTime();
        RenderSystem.bindTexture(textureId);

        boolean sizeChanged = frame.width() != width || frame.height() != height;
        if (sizeChanged || frame.full()) {
            uploadWhole(frame);
            fullUploads++;
        } else {
            uploadRegions(frame);
        }

        RenderSystem.bindTexture(0);
        note(started);
    }

    private void uploadWhole(BorrowedFrame frame) {
        // Reset the row length: a previous region might still have set it, and
        // then OpenGL would read with the wrong stride.
        RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);

        ByteBuffer pixels = frame.pixels();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                frame.width(), frame.height(), 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        width = frame.width();
        height = frame.height();
        uploadedBytes += (long) width * height * BYTES_PER_PIXEL;
    }

    private void uploadRegions(BorrowedFrame frame) {
        // <b>Here the row length pays off.</b> The buffer always carries the
        // whole image; for OpenGL to read a region out of it, it must know how
        // wide a row really is and where the region begins. Without that one
        // would have to copy the region out beforehand — and would be back at
        // one copy per image.
        RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, frame.width());
        RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer pixels = frame.pixels();

        for (DirtyRegion region : frame.dirty()) {
            RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, region.x());
            RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, region.y());
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    region.x(), region.y(), region.width(), region.height(),
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
            uploadedBytes += region.pixels() * BYTES_PER_PIXEL;
            regions++;
        }

        RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
    }

    private void note(long started) {
        uploads++;
        uploadTimes.record(System.nanoTime() - started);
    }

    /**
     * The owning path — not used today.
     *
     * <p>It is here because the interface requires it, and it is the second
     * path we want to measure later: copy, mailbox, decoupled upload. Until
     * then every path goes through {@link #upload(BorrowedFrame)}.
     */
    @Override
    public void upload(BrowserFrame frame) {
        throw new UnsupportedOperationException(
                "Der besitzende Pfad ist noch nicht gemessen — heute läuft alles "
                        + "über upload(BorrowedFrame)");
    }

    /** The texture, or zero while there is none. */
    public int textureId() {
        return textureId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ---- Measurements -----------------------------------------------------

    public long uploads() {
        return uploads;
    }

    public long uploadedBytes() {
        return uploadedBytes;
    }

    /**
     * How often the whole image went instead of only the changed region.
     *
     * <p>The number that shows whether regions arrive at all. If it equals
     * {@link #uploads()}, the saving is not working.
     */
    public long fullUploads() {
        return fullUploads;
    }

    /** How many regions were uploaded in total. */
    public long regions() {
        return regions;
    }

    /** Average duration of an upload in microseconds. */
    public double averageUploadMicros() {
        return uploadTimes.average();
    }

    public double slowestUploadMicros() {
        return uploadTimes.slowest();
    }

    /** The distribution of upload times — median, p95 and outliers. */
    public DurationSamples uploadTimes() {
        return uploadTimes;
    }

    /** Bytes per image, averaged — the number that regions are meant to keep small. */
    public double bytesPerUpload() {
        return uploads == 0 ? 0.0 : uploadedBytes / (double) uploads;
    }

    public void resetStats() {
        uploads = 0;
        uploadedBytes = 0;
        fullUploads = 0;
        regions = 0;
        uploadTimes.reset();
    }

    @Override
    public void close() {
        closed = true;
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
        width = 0;
        height = 0;
    }
}
