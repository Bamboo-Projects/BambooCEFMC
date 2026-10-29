package dev.devpanda.bamboocef.web.capture;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Fetches Minecraft's image from the graphics card and turns it into an image
 * for the browser.
 *
 * <p><b>When.</b> To be called while the main target holds the finished
 * world. Since 26.1 a screen only collects what is to be drawn, and the whole
 * interface (the HUD included) is drawn in one go afterwards. So the first line
 * in a screen's {@code extractRenderState} sees the world, but no longer the
 * HUD: that one is now drawn together with the screen.
 *
 * <p><b>And why that rules out feedback.</b> The browser is drawn <b>after</b>
 * this point. What is captured here can therefore never contain it — not even
 * one frame later, because then the capture again happens beforehand. No
 * detection is needed and no second draw buffer; the ordering alone is enough.
 *
 * <p><b>Straight through OpenGL, like the texture upload.</b> 26.1 has no
 * framebuffer ids on its render targets any more, only GPU textures. Two
 * framebuffers of our own point at them; the game's draw passes bind theirs
 * anew each time and are not disturbed by it.
 *
 * <p><b>Downscaling happens on the graphics card, not in main memory.</b>
 * {@code glBlitFramebuffer} with linear filtering costs almost nothing there
 * and shrinks what has to cross the bus afterwards: a quarter of the edge
 * length is a sixteenth of the bytes. Only then is it read — the step that is
 * genuinely expensive.
 *
 * <p>The pipeline at full length:
 *
 * <pre>
 *   Minecraft's image on the graphics card
 *     → glBlitFramebuffer (graphics card, downscaled)
 *     → glGetTexImage     (graphics card → main memory)   ← the expensive step
 *     → encoding           (main memory)
 *     → store
 *     → Chromium fetches, decodes, uploads to its graphics card
 * </pre>
 */
public final class WorldCapture implements AutoCloseable {

    /** In which format the image goes to the browser. */
    public enum Format {

        /**
         * Losslessly compressed.
         *
         * <p>Small on the wire, expensive to produce: the compression runs in
         * main memory and grows more expensive with area.
         */
        PNG("image/png"),

        /**
         * Raw data with a fifty-four-byte header in front.
         *
         * <p>Nothing to compute — the pixels go out exactly as they come from
         * the graphics card. Large on the wire, but the wire here is main
         * memory.
         *
         * <p>The happy coincidence: these images count their rows from the
         * bottom, and OpenGL textures do too. So there is nothing to flip.
         */
        BMP("image/bmp");

        private final String mimeType;

        Format(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    private RenderTarget small;
    private int readFramebuffer;
    private int drawFramebuffer;
    private int targetWidth;
    private int targetHeight;

    private final DurationSamples blitTimes = new DurationSamples();
    private final DurationSamples readTimes = new DurationSamples();
    private final DurationSamples encodeTimes = new DurationSamples();
    private long lastBytes;

    /**
     * Captures and deposits it in the store.
     *
     * <p>Must run on the render thread.
     *
     * @param scale how large relative to the screen — 1.0 is full, 0.25 is a
     *              sixteenth of the pixels
     * @return the number of the capture, or null on failure
     */
    public long captureInto(FrameStore store, double scale, Format format) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.getColorTexture() == null) {
            return 0L;
        }
        int wanted = Math.max(1, (int) Math.round(main.width * scale));
        int wantedHeight = Math.max(1, (int) Math.round(main.height * scale));

        long started = System.nanoTime();
        ensureTarget(wanted, wantedHeight);
        blit(main);
        blitTimes.record(System.nanoTime() - started);

        started = System.nanoTime();
        byte[] bytes;
        try {
            bytes = format == Format.BMP ? readAsBmp() : readAsPng(started);
        } catch (Exception broken) {
            return 0L;
        }
        lastBytes = bytes.length;
        return store.put(bytes, format.mimeType(), targetWidth, targetHeight);
    }

    /**
     * Creates the downscale target if it is missing or no longer fits.
     *
     * <p>Reused as long as the dimensions match: creating one render target per
     * capture would mean creating and freeing a texture per capture.
     */
    private void ensureTarget(int width, int height) {
        if (small != null && targetWidth == width && targetHeight == height) {
            return;
        }
        if (small != null) {
            small.destroyBuffers();
        }
        // No depth buffer: what arrives here is a finished image.
        small = new TextureTarget("bamboocef world capture", width, height, false);
        targetWidth = width;
        targetHeight = height;
    }

    private void blit(RenderTarget main) {
        if (readFramebuffer == 0) {
            readFramebuffer = GlStateManager.glGenFramebuffers();
            drawFramebuffer = GlStateManager.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, glName(main.getColorTexture()), 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, glName(small.getColorTexture()), 0);
        GlStateManager._glBlitFrameBuffer(
                0, 0, main.width, main.height,
                0, 0, targetWidth, targetHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        // <b>Bind back</b>, through GlStateManager so that its memory of the
        // bound framebuffers stays true. Every draw pass of the game binds its
        // own target afterwards anyway.
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static int glName(GpuTexture texture) {
        if (!(texture instanceof GlTexture gl)) {
            throw new IllegalStateException("Die Weltaufnahme liest über OpenGL, das Spiel zeichnet aber anders");
        }
        return gl.glId();
    }

    /**
     * Reads and compresses.
     *
     * <p>Read the texture into main memory, then encode it flipped. The flip
     * is necessary because OpenGL textures count their rows from the bottom
     * and PNG from the top; stb does it while writing, at no extra pass.
     */
    private byte[] readAsPng(long readStarted) throws Exception {
        try (NativeImage image = new NativeImage(targetWidth, targetHeight, false)) {
            GlStateManager._bindTexture(glName(small.getColorTexture()));
            PixelPack.reset();
            GL11.nglGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image.getPointer());
            GlStateManager._bindTexture(0);
            readTimes.record(System.nanoTime() - readStarted);

            long encodeStarted = System.nanoTime();
            byte[] png = PngEncoder.encode(image, true);
            encodeTimes.record(System.nanoTime() - encodeStarted);
            return png;
        }
    }

    /**
     * Reads raw and prepends a header.
     *
     * <p>It is read straight into the order this format expects — blue, green,
     * red, alpha — and the row direction comes out right on its own. Between
     * graphics card and browser there is then no computation left, only a copy.
     */
    private byte[] readAsBmp() {
        long readStarted = System.nanoTime();
        int pixelBytes = targetWidth * targetHeight * 4;
        byte[] out = new byte[54 + pixelBytes];
        java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(pixelBytes);
        try {
            GlStateManager._bindTexture(glName(small.getColorTexture()));
            PixelPack.reset();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixels);
            GlStateManager._bindTexture(0);
            readTimes.record(System.nanoTime() - readStarted);

            long encodeStarted = System.nanoTime();
            writeBmpHeader(out, targetWidth, targetHeight, pixelBytes);
            pixels.get(out, 54, pixelBytes);
            encodeTimes.record(System.nanoTime() - encodeStarted);
            return out;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(pixels);
        }
    }

    /** Fifty-four bytes that turn pixels into a file. */
    private static void writeBmpHeader(byte[] out, int width, int height, int pixelBytes) {
        out[0] = 'B';
        out[1] = 'M';
        putInt(out, 2, 54 + pixelBytes);        // file size
        putInt(out, 10, 54);                    // where the pixels begin
        putInt(out, 14, 40);                    // length of this second header
        putInt(out, 18, width);
        putInt(out, 22, height);                // positive: rows count from the bottom
        out[26] = 1;                            // one plane
        out[28] = 32;                           // thirty-two bits per pixel
        putInt(out, 34, pixelBytes);
    }

    private static void putInt(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }

    // ---- Measurements -----------------------------------------------------

    public DurationSamples blitTimes() {
        return blitTimes;
    }

    public DurationSamples readTimes() {
        return readTimes;
    }

    public DurationSamples encodeTimes() {
        return encodeTimes;
    }

    public long lastBytes() {
        return lastBytes;
    }

    public int targetWidth() {
        return targetWidth;
    }

    public int targetHeight() {
        return targetHeight;
    }

    public void resetStats() {
        blitTimes.reset();
        readTimes.reset();
        encodeTimes.reset();
    }

    @Override
    public void close() {
        if (small != null) {
            small.destroyBuffers();
            small = null;
        }
        if (readFramebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(readFramebuffer);
            GlStateManager._glDeleteFramebuffers(drawFramebuffer);
            readFramebuffer = 0;
            drawFramebuffer = 0;
        }
        targetWidth = 0;
        targetHeight = 0;
    }
}
