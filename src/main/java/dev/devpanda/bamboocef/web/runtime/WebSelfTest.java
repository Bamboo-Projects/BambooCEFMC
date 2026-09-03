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
 * Checks the frame path without anyone having to look.
 *
 * <p><b>Why this cannot be an ordinary test run.</b> The path from Chromium
 * into a texture needs a drawing context, and that exists only in the running
 * game. A GameTest runs on the server and has none.
 *
 * <p><b>And why the eye still does not decide.</b> "Looks right" does not
 * answer the three questions that matter: are the color channels the right way
 * round, is the image the right way up, and does the alpha come through? An
 * image in which red and blue are swapped looks plausible, until someone tries
 * to recognize a color.
 *
 * <p>That is why this run loads a page with four known fields, reads the
 * finished texture back from the graphics card, and compares the pixels. What
 * it reports is verifiable and goes into the log.
 *
 * <p>It runs once per session, a few seconds after startup — by then MCEF has
 * come up. Afterwards it tears everything down again.
 */
public final class WebSelfTest {

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/WebSelfTest");

    /** Edge length of the test surface. Small enough to read back quickly. */
    private static final int SIZE = 256;

    /**
     * Four fields with known colors, plus a transparent one.
     *
     * <p>The arrangement is the test: top left red, top right green, bottom
     * left blue. Whoever flips the image vertically finds blue at the top — and
     * whoever swaps channels finds blue at the top left. Both thus show up
     * without anyone looking.
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
     * Once per session, called from the client's tick.
     *
     * <p>Waits a few seconds first: MCEF comes up alongside, and whoever asks
     * too early measures the startup instead of the frame path.
     */
    public static void tick() {
        if (done) {
            return;
        }
        ticksWaited++;
        if (ticksWaited < 200) {          // about ten seconds
            return;
        }
        if (session == null) {
            start();
            return;
        }
        // <b>One frame is enough, and no more come anyway.</b> A page that does
        // not change paints exactly once — after that Chromium sends nothing
        // more. Whoever waits for a second waits forever; that is exactly what
        // happened here first and looked like a broken render path.
        //
        // Incidentally, that is the best news for the cost question: a resting
        // editor costs not a single upload.
        if (session.paints() >= 1) {
            check();
        } else if (ticksWaited % 100 == 0) {
            // Progress instead of silence: whoever only reports at the end does
            // not know afterwards whether the browser was loading, hung, or
            // never started.
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
     * Whether the self-test is through.
     *
     * <p>The measurement asks about this: two browsers at once measure each
     * other in, and the self-test comes first — it decides whether there is
     * anything to measure at all.
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
        // <b>No data: URL.</b> Chromium has refused it as a main document since
        // version 60 — a security measure against phishing. The browser then
        // loads nothing at all, consequently never paints, and it looks like a
        // broken render path. A file does the job just as well.
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
            // Read back in the same layout in which it was uploaded.
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

    /** One pixel as r, g, b, a. */
    private static int[] pixel(ByteBuffer buffer, int x, int y) {
        int offset = (y * SIZE + x) * 4;
        // BGRA in memory, the way Chromium and OpenGL handle it here.
        int b = buffer.get(offset) & 0xFF;
        int g = buffer.get(offset + 1) & 0xFF;
        int r = buffer.get(offset + 2) & 0xFF;
        int a = buffer.get(offset + 3) & 0xFF;
        return new int[] {r, g, b, a};
    }

    private static void report(String where, int[] found, int r, int g, int b) {
        // Generous: Chromium may round, and scaling can blend at the edges.
        // What should stand out here is a swapped channel or a mirrored image —
        // both are far off.
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
