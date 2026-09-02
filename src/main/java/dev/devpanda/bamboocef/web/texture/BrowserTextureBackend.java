package dev.devpanda.bamboocef.web.texture;

import dev.devpanda.bamboocef.web.frame.BrowserFrame;

/**
 * Where images go.
 *
 * <p><b>What is missing here is intentional:</b> no texture name, no
 * {@code ByteBuffer}, no handle. A backend that accepts an image is
 * interchangeable; one that hands out an OpenGL name fixes the whole path.
 *
 * <p>How the image becomes visible is decided by whoever built the backend —
 * that party knows both ends. This interface only knows that there is one.
 */
public interface BrowserTextureBackend extends AutoCloseable {

    /**
     * Takes over an image.
     *
     * <p>The image still belongs to the caller afterwards: the backend copies
     * what it needs and holds on to nothing. The caller can therefore reuse it
     * immediately afterwards.
     *
     * <p>Must be called where the draw context is valid — for Minecraft that is
     * the render thread. A backend may rely on it.
     */
    void upload(BrowserFrame frame);

    /** Releases everything. Closing more than once is allowed. */
    @Override
    void close();
}
