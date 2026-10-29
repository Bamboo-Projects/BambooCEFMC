package dev.devpanda.bamboocef.web.texture;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.devpanda.bamboocef.web.frame.BorrowedFrame;
import dev.devpanda.bamboocef.web.frame.BrowserFrame;
import dev.devpanda.bamboocef.web.frame.DirtyRegion;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import dev.devpanda.bamboocef.web.view.ManagedTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
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
 * <p><b>The texture belongs to Minecraft's GPU layer, the upload does not.</b>
 * Since 26.1 the game only draws what it created itself: a {@link GpuTexture}
 * with a view and a sampler. So the texture is made there. The upload, though,
 * still goes straight through OpenGL, because the game's own
 * {@code writeToTexture} knows neither BGRA nor a row length; it would cost a
 * copy and a swap of red and blue per image. 26.1 runs on OpenGL only. Should
 * a second backend arrive, {@link #upload(BorrowedFrame)} says so loudly
 * instead of drawing garbage.
 *
 * <p>All calls belong on the render thread. That is a given today: Chromium's
 * message loop is pumped there, so {@code onPaint} arrives there too.
 */
public final class GlTextureBackend implements BrowserTextureBackend {

    /** Four bytes per pixel. */
    private static final int BYTES_PER_PIXEL = 4;

    private static int nextName;

    private GpuTexture texture;
    private GpuTextureView view;
    private Identifier location;
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
     * Creates the texture in the size of the image.
     *
     * <p>A GPU texture has a fixed size. A new size therefore means a new
     * texture. The old one is not closed here but at the end of the frame, see
     * {@link RetiredTextures}: a draw collected earlier in this frame may still
     * refer to it.
     */
    private void allocate(int newWidth, int newHeight) {
        release();
        // No mipmaps: a web interface changes every frame, and a chain of
        // smaller copies would have to be rebuilt every time. Filtering is the
        // sampler's business, see sampler().
        texture = RenderSystem.getDevice().createTexture(
                () -> "bamboocef browser",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8, newWidth, newHeight, 1, 1);
        view = RenderSystem.getDevice().createTextureView(texture);
        width = newWidth;
        height = newHeight;
    }

    private void release() {
        RetiredTextures.afterFrame(view, texture);
        view = null;
        texture = null;
        width = 0;
        height = 0;
    }

    /**
     * Uploads a borrowed image    /**
     * Uploads a borrowed image — the cheapest way there is.
     *
     * <p>The buffer belongs to Chromium and is valid only during this call.
     * That is exactly why {@link BorrowedFrame} is here and not
     * {@link BrowserFrame}: what is only borrowed should be named accordingly.
     */
    public void upload(BorrowedFrame frame) {
        if (closed || frame.width() <= 0 || frame.height() <= 0) {
            return;
        }
        long started = System.nanoTime();
        boolean sizeChanged = frame.width() != width || frame.height() != height;
        if (sizeChanged) {
            allocate(frame.width(), frame.height());
        }
        if (!(texture instanceof GlTexture gl)) {
            throw new IllegalStateException("BambooCEF lädt Bilder über OpenGL hoch, "
                    + "das Spiel zeichnet aber mit " + RenderSystem.getDevice().getBackendName());
        }
        // Through GlStateManager and not GL11 directly: Minecraft remembers
        // which texture is bound, and a bind past it would leave that memory
        // wrong.
        GlStateManager._bindTexture(gl.glId());

        if (sizeChanged || frame.full()) {
            uploadWhole(frame);
            fullUploads++;
        } else {
            uploadRegions(frame);
        }

        GlStateManager._bindTexture(0);
        note(started);
    }

    private void uploadWhole(BorrowedFrame frame) {
        // Reset the row length: a previous region might still have set it, and
        // then OpenGL would read with the wrong stride.
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);

        ByteBuffer pixels = frame.pixels();
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                frame.width(), frame.height(),
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        uploadedBytes += (long) width * height * BYTES_PER_PIXEL;
    }

    private void uploadRegions(BorrowedFrame frame) {
        // <b>Here the row length pays off.</b> The buffer always carries the
        // whole image; for OpenGL to read a region out of it, it must know how
        // wide a row really is and where the region begins. Without that one
        // would have to copy the region out beforehand — and would be back at
        // one copy per image.
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, frame.width());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer pixels = frame.pixels();

        for (DirtyRegion region : frame.dirty()) {
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, region.x());
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, region.y());
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0,
                    region.x(), region.y(), region.width(), region.height(),
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
            uploadedBytes += region.pixels() * BYTES_PER_PIXEL;
            regions++;
        }

        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
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

    /**
     * The OpenGL name of the texture, or zero while there is none.
     *
     * <p>Today mostly the answer to "is there an image yet?"; drawing goes
     * through {@link #textureView()}.
     */
    public int textureId() {
        return texture instanceof GlTexture gl && !gl.isClosed() ? gl.glId() : 0;
    }

    /**
     * The name under which Minecraft finds this texture.
     *
     * <p>Registered on the first question and not earlier: most sessions are
     * only ever asked once they have an image. The name stays the same across
     * resizes, see {@link ManagedTexture}.
     */
    public Identifier location() {
        if (location == null && !closed) {
            location = Identifier.fromNamespaceAndPath("bamboocef", "browser/" + nextName++);
            Minecraft.getInstance().getTextureManager()
                    .register(location, new ManagedTexture(this::textureView, sampler()));
        }
        return location;
    }

    /** What Minecraft draws with, or {@code null} while there is no image yet. */
    public GpuTextureView textureView() {
        return view;
    }

    /**
     * Linear and clamped at the edge.
     *
     * <p>Linear, because a web interface is rarely shown at its own resolution
     * and hard edges on text look worse than a soft transition. Clamped,
     * because at the edge of a surface there should be no second one.
     */
    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
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
        // Unregister first, then release: the other way around would leave a
        // name registered that points into the void.
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            location = null;
        }
        release();
    }
}
