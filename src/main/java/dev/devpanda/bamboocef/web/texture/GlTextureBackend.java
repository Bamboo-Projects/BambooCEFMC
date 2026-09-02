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
 * Lädt Browserbilder in eine OpenGL-Textur.
 *
 * <p><b>Ohne {@code NativeImage} und ohne {@code DynamicTexture}.</b> Beide
 * hätten eine zusätzliche Kopie im Hauptspeicher bedeutet: Ein
 * {@code NativeImage} besitzt seinen eigenen Puffer, und um Chromiums Bild
 * hineinzubekommen, müsste man es dort hineinschreiben, bevor es auf die
 * Grafikkarte geht. Der Weg hier gibt LWJGL den geliehenen Puffer direkt —
 * eine Kopie weniger je Bild, bei 1080p achteinhalb Megabyte.
 *
 * <p><b>Das Format ist nicht verhandelbar.</b> Chromium liefert BGRA mit
 * Ursprung oben links. {@code GL_BGRA} zusammen mit
 * {@code GL_UNSIGNED_INT_8_8_8_8_REV} ist die Kombination, die das ohne
 * Umsortieren übernimmt; jede andere kostet entweder einen Shader oder eine
 * Schleife über jeden Bildpunkt.
 *
 * <p><b>Zum Ursprung oben links:</b> OpenGL-Texturen zählen von unten. Hier
 * wird trotzdem nicht gespiegelt — das kostete eine weitere Kopie. Wer das
 * Bild zeichnet, dreht stattdessen die Texturkoordinaten. Spiegeln beim
 * Hochladen wäre die teuerste Stelle für die billigste Rechnung.
 *
 * <p>Alle Aufrufe gehören in den Render-Thread. Das ist heute gegeben:
 * Chromiums Nachrichtenschleife wird dort gepumpt, also kommt auch
 * {@code onPaint} dort an.
 */
public final class GlTextureBackend implements BrowserTextureBackend {

    /** Vier Byte je Bildpunkt. */
    private static final int BYTES_PER_PIXEL = 4;

    private int textureId;
    private int width;
    private int height;
    private boolean closed;

    /** Wie viele Bytes bisher hochgeladen wurden — für die Messung. */
    private long uploadedBytes;
    private long uploads;
    private long fullUploads;
    private long regions;
    private final DurationSamples uploadTimes = new DurationSamples();

    /**
     * Legt die Textur an.
     *
     * <p>Muss im Render-Thread laufen. Vorher gibt es keine Textur, und
     * {@link #textureId()} antwortet mit null.
     */
    public void initialize() {
        if (closed || textureId != 0) {
            return;
        }
        textureId = GL11.glGenTextures();
        RenderSystem.bindTexture(textureId);
        // Linear und nicht nächster Nachbar: Eine Web-Oberfläche wird selten
        // in ihrer eigenen Auflösung gezeigt, und harte Kanten auf Schrift
        // sehen schlechter aus als ein weicher Übergang.
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        // Ohne Wiederholung: Am Rand einer Oberfläche gehört keine zweite.
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.bindTexture(0);
    }

    /**
     * Lädt ein geliehenes Bild hoch — der billigste Weg, den es gibt.
     *
     * <p>Der Puffer gehört Chromium und ist nur während dieses Aufrufs
     * gültig. Genau deshalb steht hier {@link BorrowedFrame} und nicht
     * {@link BrowserFrame}: Was nur geliehen ist, soll auch nur so heißen.
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
        // Zeilenlänge zurücksetzen: Ein voriger Ausschnitt könnte sie noch
        // gesetzt haben, und dann läse OpenGL mit falscher Schrittweite.
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
        // <b>Hier zahlt sich die Zeilenlänge aus.</b> Der Puffer trägt immer
        // das ganze Bild; damit OpenGL einen Ausschnitt daraus liest, muss es
        // wissen, wie breit eine Zeile wirklich ist und wo der Ausschnitt
        // beginnt. Ohne das müsste man den Ausschnitt vorher herauskopieren —
        // und wäre wieder bei einer Kopie je Bild.
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
     * Der besitzende Weg — heute nicht benutzt.
     *
     * <p>Er steht hier, weil die Schnittstelle ihn verlangt, und er ist der
     * zweite Pfad, den wir später messen wollen: Kopie, Postfach,
     * entkoppelter Upload. Bis dahin führt jeder Weg über
     * {@link #upload(BorrowedFrame)}.
     */
    @Override
    public void upload(BrowserFrame frame) {
        throw new UnsupportedOperationException(
                "Der besitzende Pfad ist noch nicht gemessen — heute läuft alles "
                        + "über upload(BorrowedFrame)");
    }

    /** Die Textur, oder null, solange es keine gibt. */
    public int textureId() {
        return textureId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ---- Messwerte --------------------------------------------------------

    public long uploads() {
        return uploads;
    }

    public long uploadedBytes() {
        return uploadedBytes;
    }

    /**
     * Wie oft das ganze Bild ging statt nur der geänderte Ausschnitt.
     *
     * <p>Die Zahl, an der sich zeigt, ob Ausschnitte überhaupt ankommen. Ist
     * sie gleich {@link #uploads()}, arbeitet die Ersparnis nicht.
     */
    public long fullUploads() {
        return fullUploads;
    }

    /** Wie viele Ausschnitte insgesamt hochgeladen wurden. */
    public long regions() {
        return regions;
    }

    /** Durchschnittliche Dauer eines Uploads in Mikrosekunden. */
    public double averageUploadMicros() {
        return uploadTimes.average();
    }

    public double slowestUploadMicros() {
        return uploadTimes.slowest();
    }

    /** Die Verteilung der Uploadzeiten — Median, p95 und Ausreißer. */
    public DurationSamples uploadTimes() {
        return uploadTimes;
    }

    /** Bytes je Bild, gemittelt — die Zahl, die Ausschnitte klein machen sollen. */
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
