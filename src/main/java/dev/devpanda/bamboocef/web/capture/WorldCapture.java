package dev.devpanda.bamboocef.web.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.bamboocef.web.measure.DurationSamples;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Holt Minecrafts Bild von der Grafikkarte und macht es zu einem Bild für den
 * Browser.
 *
 * <p><b>Wann.</b> Aufzurufen, solange im Hauptziel <b>Welt und
 * Bedienoberfläche</b> stehen, aber noch kein eigener Bildschirm. In
 * Minecrafts Ablauf ist das die Zeit zwischen {@code gui.render(…)} und dem
 * Zeichnen des offenen Bildschirms — praktisch also die erste Zeile in dessen
 * {@code render}, vor jedem eigenen Strich.
 *
 * <p><b>Und warum das die Rückkopplung ausschließt.</b> Der Browser wird
 * <b>nach</b> diesem Punkt gezeichnet. Was hier aufgenommen wird, kann ihn
 * deshalb nie enthalten — auch nicht ein Bild später, denn dann wird wieder
 * vorher aufgenommen. Es braucht keine Erkennung und keinen zweiten
 * Zeichenpuffer; die Reihenfolge allein genügt.
 *
 * <p><b>Verkleinert wird auf der Grafikkarte, nicht im Hauptspeicher.</b>
 * {@code glBlitFramebuffer} mit linearer Filterung kostet dort fast nichts und
 * verkleinert das, was danach über den Bus muss: Ein Viertel der Kantenlänge
 * ist ein Sechzehntel der Bytes. Erst danach wird gelesen — der Schritt, der
 * wirklich teuer ist.
 *
 * <p>Die Pipeline in voller Länge:
 *
 * <pre>
 *   Minecrafts Bild auf der Grafikkarte
 *     → glBlitFramebuffer (Grafikkarte, verkleinert)
 *     → glGetTexImage     (Grafikkarte → Hauptspeicher)   ← der teure Schritt
 *     → Kodierung          (Hauptspeicher)
 *     → Ablage
 *     → Chromium holt ab, dekodiert, lädt auf seine Grafikkarte
 * </pre>
 */
public final class WorldCapture implements AutoCloseable {

    /** In welchem Format das Bild an den Browser geht. */
    public enum Format {

        /**
         * Verlustfrei komprimiert.
         *
         * <p>Klein auf der Leitung, teuer beim Erzeugen: Die Kompression läuft
         * im Hauptspeicher und wird mit der Fläche teurer.
         */
        PNG("image/png"),

        /**
         * Rohdaten mit vierundfünfzig Byte Kopf davor.
         *
         * <p>Nichts zu rechnen — die Bildpunkte gehen so hinaus, wie sie von
         * der Grafikkarte kommen. Groß auf der Leitung, aber die Leitung ist
         * hier der Hauptspeicher.
         *
         * <p>Der glückliche Zufall: Diese Bilder zählen ihre Zeilen von unten,
         * und OpenGL-Texturen tun das auch. Es ist also nichts zu spiegeln.
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
    private int targetWidth;
    private int targetHeight;

    private final DurationSamples blitTimes = new DurationSamples();
    private final DurationSamples readTimes = new DurationSamples();
    private final DurationSamples encodeTimes = new DurationSamples();
    private long lastBytes;

    /**
     * Nimmt auf und legt in der Ablage ab.
     *
     * <p>Muss im Render-Thread laufen.
     *
     * @param scale wie groß im Verhältnis zum Bildschirm — 1,0 ist voll,
     *              0,25 ist ein Sechzehntel der Bildpunkte
     * @return die Nummer der Aufnahme, oder null bei einem Fehlschlag
     */
    public long captureInto(FrameStore store, double scale, Format format) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
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
     * Legt das Verkleinerungsziel an, wenn es fehlt oder nicht mehr passt.
     *
     * <p>Wiederverwendet, solange die Maße stimmen: Ein Zeichenziel je
     * Aufnahme anzulegen hieße, je Aufnahme eine Textur zu erzeugen und wieder
     * freizugeben.
     */
    private void ensureTarget(int width, int height) {
        if (small != null && targetWidth == width && targetHeight == height) {
            return;
        }
        if (small != null) {
            small.destroyBuffers();
        }
        // Ohne Tiefenpuffer: Was hier ankommt, ist ein fertiges Bild.
        small = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        small.setClearColor(0f, 0f, 0f, 1f);
        targetWidth = width;
        targetHeight = height;
    }

    private void blit(RenderTarget main) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, small.frameBufferId);
        GL30.glBlitFramebuffer(
                0, 0, main.width, main.height,
                0, 0, targetWidth, targetHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        // <b>Zurückbinden, sonst zeichnet Minecraft ins falsche Ziel.</b> Was
        // danach kommt — der eigene Bildschirm — ginge sonst in die kleine
        // Textur und wäre auf dem Schirm nicht zu sehen.
        main.bindWrite(true);
    }

    /**
     * Liest und komprimiert.
     *
     * <p>Der Weg von Minecrafts eigenem Bildschirmfoto: Textur binden,
     * herunterladen, senkrecht spiegeln. Das Spiegeln ist nötig, weil
     * OpenGL-Texturen ihre Zeilen von unten zählen und PNG von oben.
     */
    private byte[] readAsPng(long readStarted) throws Exception {
        try (NativeImage image = new NativeImage(targetWidth, targetHeight, false)) {
            RenderSystem.bindTexture(small.getColorTextureId());
            image.downloadTexture(0, true);
            image.flipY();
            readTimes.record(System.nanoTime() - readStarted);

            long encodeStarted = System.nanoTime();
            byte[] png = image.asByteArray();
            encodeTimes.record(System.nanoTime() - encodeStarted);
            return png;
        }
    }

    /**
     * Liest roh und setzt einen Kopf davor.
     *
     * <p>Gelesen wird gleich in der Anordnung, die dieses Format erwartet —
     * Blau, Grün, Rot, Alpha —, und die Zeilenrichtung stimmt von selbst.
     * Zwischen Grafikkarte und Browser liegt damit keine Rechnung mehr, nur
     * noch eine Kopie.
     */
    private byte[] readAsBmp() {
        long readStarted = System.nanoTime();
        int pixelBytes = targetWidth * targetHeight * 4;
        byte[] out = new byte[54 + pixelBytes];
        java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(pixelBytes);
        try {
            RenderSystem.bindTexture(small.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixels);
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

    /** Vierundfünfzig Byte, die aus Bildpunkten eine Datei machen. */
    private static void writeBmpHeader(byte[] out, int width, int height, int pixelBytes) {
        out[0] = 'B';
        out[1] = 'M';
        putInt(out, 2, 54 + pixelBytes);        // Dateigröße
        putInt(out, 10, 54);                    // wo die Bildpunkte beginnen
        putInt(out, 14, 40);                    // Länge dieses zweiten Kopfes
        putInt(out, 18, width);
        putInt(out, 22, height);                // positiv: Zeilen zählen von unten
        out[26] = 1;                            // eine Ebene
        out[28] = 32;                           // zweiunddreißig Bit je Bildpunkt
        putInt(out, 34, pixelBytes);
    }

    private static void putInt(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }

    // ---- Messwerte --------------------------------------------------------

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
        targetWidth = 0;
        targetHeight = 0;
    }
}
