package dev.devpanda.bamboocef.web.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Prüft den Bildweg, ohne dass jemand hinsehen muss.
 *
 * <p><b>Warum das kein gewöhnlicher Prüflauf sein kann.</b> Der Weg von
 * Chromium in eine Textur braucht einen Zeichenkontext, und den gibt es nur
 * im laufenden Spiel. Ein GameTest läuft auf dem Server und hat keinen.
 *
 * <p><b>Und warum trotzdem nicht das Auge entscheidet.</b> „Sieht richtig
 * aus" beantwortet die drei Fragen nicht, auf die es ankommt: Sind die
 * Farbkanäle richtig herum, steht das Bild richtig herum, und kommt das Alpha
 * an? Ein Bild, bei dem Rot und Blau vertauscht sind, sieht plausibel aus,
 * bis jemand eine Farbe wiedererkennen will.
 *
 * <p>Deshalb lädt dieser Lauf eine Seite mit vier bekannten Feldern, liest
 * die fertige Textur von der Grafikkarte zurück und vergleicht die
 * Bildpunkte. Was er meldet, ist nachprüfbar und steht im Protokoll.
 *
 * <p>Er läuft einmal je Sitzung, ein paar Sekunden nach dem Start — dann ist
 * MCEF hochgekommen. Danach räumt er alles wieder ab.
 */
public final class WebSelfTest {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebSelfTest");

    /** Kantenlänge der Prüffläche. Klein genug, um schnell zurückzulesen. */
    private static final int SIZE = 256;

    /**
     * Vier Felder mit bekannten Farben, dazu ein durchsichtiges.
     *
     * <p>Die Anordnung ist die Prüfung: Oben links Rot, oben rechts Grün,
     * unten links Blau. Wer das Bild senkrecht spiegelt, findet Blau oben —
     * und wer Kanäle vertauscht, findet Blau links oben. Beides fällt damit
     * auf, ohne dass jemand hinsieht.
     */
    private static final String PAGE = """
            <!doctype html><html><head><style>
              html,body{margin:0;padding:0;width:100%;height:100%;background:transparent}
              .grid{display:grid;grid-template-columns:1fr 1fr;
                    grid-template-rows:1fr 1fr;width:100vw;height:100vh}
              .a{background:rgb(255,0,0)}
              .b{background:rgb(0,255,0)}
              .c{background:rgb(0,0,255)}
              .d{background:rgba(255,255,255,0.5)}
            </style></head><body><div class="grid">
              <div class="a"></div><div class="b"></div>
              <div class="c"></div><div class="d"></div>
            </div></body></html>
            """;

    private static boolean done;
    private static BrowserSession session;
    private static int ticksWaited;

    private WebSelfTest() {
    }

    /**
     * Einmal je Sitzung, vom Takt des Clients gerufen.
     *
     * <p>Wartet erst ein paar Sekunden: MCEF fährt sich nebenher hoch, und
     * wer zu früh fragt, misst das Hochfahren statt des Bildwegs.
     */
    public static void tick() {
        if (done) {
            return;
        }
        ticksWaited++;
        if (ticksWaited < 200) {          // etwa zehn Sekunden
            return;
        }
        if (session == null) {
            start();
            return;
        }
        // <b>Ein Bild reicht, und mehr kommen auch nicht.</b> Eine Seite, die
        // sich nicht ändert, malt genau einmal — danach schickt Chromium
        // nichts mehr. Wer auf ein zweites wartet, wartet für immer; genau das
        // ist hier zuerst passiert und sah aus wie ein kaputter Renderpfad.
        //
        // Nebenbei ist das die beste Nachricht für die Kostenfrage: Ein
        // ruhender Editor kostet keinen einzigen Upload.
        if (session.paints() >= 1) {
            check();
        } else if (ticksWaited % 100 == 0) {
            // Zwischenstand statt Schweigen: Wer nur am Ende meldet, weiß
            // hinterher nicht, ob der Browser lud, hing oder nie anfing.
            LOG.info("Selbsttest: warte auf das erste Bild — bisher {} Bilder, URL {}",
                    session.paints(), session.currentUrl());
        }
        if (ticksWaited > 600) {
            LOG.warn("Nach dreißig Sekunden kam kein Bild. Zuletzt geladen: {}",
                    session.currentUrl());
            finish();
        }
    }

    /**
     * Ob der Selbsttest durch ist.
     *
     * <p>Die Messung fragt danach: Zwei Browser gleichzeitig messen einander
     * mit, und der Selbsttest kommt zuerst — er entscheidet, ob überhaupt
     * etwas zu messen ist.
     */
    public static boolean finished() {
        return done;
    }

    private static void start() {
        WebRuntimeStatus status = WebSupport.ensureStarted();
        if (!status.usable()) {
            LOG.info("Selbsttest übersprungen: {}", status);
            done = true;
            return;
        }
        // <b>Keine data:-URL.</b> Chromium verweigert sie als Hauptdokument
        // seit Version 60 — eine Sicherheitsmaßnahme gegen Phishing. Der
        // Browser lädt dann gar nichts, malt folglich nie, und es sieht aus
        // wie ein kaputter Renderpfad. Eine Datei tut es genauso.
        String url;
        try {
            java.nio.file.Path page = java.nio.file.Files.createTempFile(
                    "fn-webselftest", ".html");
            java.nio.file.Files.writeString(page, PAGE, StandardCharsets.UTF_8);
            page.toFile().deleteOnExit();
            url = page.toUri().toString();
        } catch (Exception broken) {
            LOG.warn("Selbsttest: Prüfseite ließ sich nicht ablegen", broken);
            done = true;
            return;
        }
        try {
            session = BrowserSession.open(url, true, SIZE, SIZE,
                    BrowserVisibility.FOREGROUND);
            LOG.info("Selbsttest: Browser geöffnet, {}x{}", SIZE, SIZE);
        } catch (Throwable broken) {
            LOG.warn("Selbsttest: Browser ließ sich nicht öffnen", broken);
            done = true;
        }
    }

    private static void check() {
        try {
            int texture = session.textureId();
            if (texture == 0) {
                LOG.warn("Selbsttest: keine Textur entstanden");
                finish();
                return;
            }
            ByteBuffer read = ByteBuffer
                    .allocateDirect(session.width() * session.height() * 4)
                    .order(ByteOrder.nativeOrder());
            RenderSystem.bindTexture(texture);
            // Zurücklesen in derselben Anordnung, in der hochgeladen wurde.
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, read);
            RenderSystem.bindTexture(0);

            int quarter = session.width() / 4;
            int threeQuarters = quarter * 3;
            report("oben links",  pixel(read, quarter, quarter), 255, 0, 0);
            report("oben rechts", pixel(read, threeQuarters, quarter), 0, 255, 0);
            report("unten links", pixel(read, quarter, threeQuarters), 0, 0, 255);

            int[] glass = pixel(read, threeQuarters, threeQuarters);
            LOG.info("Selbsttest: unten rechts (halbdurchsichtig) rgba({}, {}, {}, {}) "
                            + "— erwartet ein Alpha um 128",
                    glass[0], glass[1], glass[2], glass[3]);

            var stats = session.texture();
            LOG.info("Selbsttest: {} Bilder, erstes nach {} ms, {} Uploads, "
                            + "{} KB gesamt, im Mittel {} µs, langsamster {} µs",
                    session.paints(),
                    String.format(java.util.Locale.GERMANY, "%.0f",
                            session.millisToFirstPaint()),
                    stats.uploads(), stats.uploadedBytes() / 1024,
                    String.format(java.util.Locale.GERMANY, "%.1f",
                            stats.averageUploadMicros()),
                    String.format(java.util.Locale.GERMANY, "%.1f",
                            stats.slowestUploadMicros()));
        } catch (Throwable broken) {
            LOG.warn("Selbsttest ist gescheitert", broken);
        } finally {
            finish();
        }
    }

    /** Ein Bildpunkt als r, g, b, a. */
    private static int[] pixel(ByteBuffer buffer, int x, int y) {
        int offset = (y * SIZE + x) * 4;
        // BGRA im Speicher, wie Chromium und OpenGL es hier führen.
        int b = buffer.get(offset) & 0xFF;
        int g = buffer.get(offset + 1) & 0xFF;
        int r = buffer.get(offset + 2) & 0xFF;
        int a = buffer.get(offset + 3) & 0xFF;
        return new int[] {r, g, b, a};
    }

    private static void report(String where, int[] found, int r, int g, int b) {
        // Großzügig: Chromium darf runden, und eine Skalierung kann an den
        // Rändern mischen. Was hier auffallen soll, ist ein vertauschter
        // Kanal oder ein gespiegeltes Bild — beides liegt weit daneben.
        boolean ok = Math.abs(found[0] - r) < 40
                && Math.abs(found[1] - g) < 40
                && Math.abs(found[2] - b) < 40;
        if (ok) {
            LOG.info("Selbsttest: {} stimmt — rgb({}, {}, {})", where,
                    found[0], found[1], found[2]);
        } else {
            LOG.warn("Selbsttest: {} FALSCH — gefunden rgb({}, {}, {}), erwartet rgb({}, {}, {}). "
                            + "Vertauschte Farbkanäle oder ein gespiegeltes Bild.",
                    where, found[0], found[1], found[2], r, g, b);
        }
    }

    private static void finish() {
        if (session != null) {
            session.close();
            session = null;
        }
        done = true;
    }
}
