package dev.devpanda.bamboocef.web.capture;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImageWrite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Turns an image in main memory into PNG bytes.
 *
 * <p><b>Why this exists.</b> Until 1.21.1 {@code NativeImage.asByteArray()}
 * did it. Since 26.1 the game only writes PNGs into files; the encoder behind
 * it is the same stb, and it is called here directly.
 */
public final class PngEncoder {

    /**
     * Encodes an RGBA image.
     *
     * @param flip whether the rows go out bottom first: images read back from
     *             the graphics card count their rows from the bottom, PNG
     *             counts from the top
     */
    public static byte[] encode(NativeImage image, boolean flip) throws IOException {
        if (image.format() != NativeImage.Format.RGBA) {
            throw new IOException("Nur RGBA lässt sich als PNG schreiben, nicht " + image.format());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int components = image.format().components();
        int stride = image.getWidth() * components;
        try (STBIWriteCallback sink = STBIWriteCallback.create((context, data, size) -> {
            ByteBuffer chunk = STBIWriteCallback.getData(data, size);
            byte[] bytes = new byte[size];
            chunk.get(bytes);
            out.write(bytes, 0, size);
        })) {
            // The flip is a global switch in stb; it is set for this one call
            // and put back afterwards.
            STBImageWrite.stbi_flip_vertically_on_write(flip);
            try {
                int ok = STBImageWrite.nstbi_write_png_to_func(sink.address(), 0L,
                        image.getWidth(), image.getHeight(), components, image.getPointer(), stride);
                if (ok == 0) {
                    throw new IOException("stb konnte das PNG nicht schreiben");
                }
            } finally {
                STBImageWrite.stbi_flip_vertically_on_write(false);
            }
        }
        return out.toByteArray();
    }

    private PngEncoder() {
    }
}
