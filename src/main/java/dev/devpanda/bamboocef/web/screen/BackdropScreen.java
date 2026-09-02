package dev.devpanda.bamboocef.web.screen;

import dev.devpanda.bamboocef.web.capture.FrameStore;
import dev.devpanda.bamboocef.web.capture.WorldCapture;
import dev.devpanda.bamboocef.web.runtime.FrameSchemes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A screen behind which Minecraft keeps running — in the document, not behind
 * it.
 *
 * <p><b>Why Minecraft's image has to be an element of the page.</b> The
 * obvious idea would be to make the browser transparent and simply leave
 * Minecraft beneath it. For soft glass that is not enough: per the CSS
 * specification, what a {@code backdrop-filter} filters covers exclusively
 * content of <b>this document</b>. What lies behind the browser window is not
 * part of it — the filter would run over transparency and yield nothing.
 *
 * <p>So Minecraft's image is captured and inserted as the bottommost element.
 * From then on it is ordinary page content for Chromium, and everything CSS
 * can do applies to it.
 *
 * <p><b>Two beats that have nothing to do with each other.</b> How often a new
 * backdrop comes into being {@link Mode} decides. How often the browser
 * paints, the page decides itself — for a resting editor not at all. A
 * backdrop at two frames per second therefore does not mean that anything else
 * runs at two frames per second.
 *
 * <p><b>The swap happens without a reload.</b> A single JavaScript call sets
 * the image's address anew; the page stays up, with everything typed into it.
 * This is explicitly not a bridge between Java and the page — it goes in one
 * direction only, knows exactly one command, and the page can call nothing
 * back.
 */
public class BackdropScreen extends BrowserScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/BackdropScreen");

    /** How often a new backdrop comes into being. */
    public enum Mode {

        /**
         * Once on opening, never again after that.
         *
         * <p>Possibly enough for an editor: whoever is writing looks at the
         * text anyway and not through it. And a still image costs exactly
         * nothing after the first frame.
         */
        STATIC(0),

        /** Twice per second — visibly stuttering, but alive. */
        LOW_2(2),

        /** Five times per second. */
        LOW_5(5),

        /** Ten times per second. */
        LOW_10(10);

        private final int framesPerSecond;

        Mode(int framesPerSecond) {
            this.framesPerSecond = framesPerSecond;
        }

        public int framesPerSecond() {
            return framesPerSecond;
        }

        public long intervalNanos() {
            return framesPerSecond <= 0 ? Long.MAX_VALUE : 1_000_000_000L / framesPerSecond;
        }
    }

    /**
     * The page: Minecraft's image at the bottom, glass on top.
     *
     * <p>Deliberately without a script for the image swap — that comes from
     * outside. A page that itself polled at short intervals would poll even
     * when there is nothing new.
     */
    private static final String PAGE = """
            <!doctype html><html lang="de"><head><meta charset="utf-8"><style>
              html,body{margin:0;width:100%;height:100%;overflow:hidden;
                        background:#0a0b10;color:#e8eaf2;
                        font:15px/1.6 "Segoe UI",system-ui,sans-serif}
              /* Ohne Quelle zeigt ein Bild sonst ein Bruchsymbol — sichtbar
                 im Augenblick zwischen Laden und erster Aufnahme. */
              #minecraft-background{position:fixed;inset:0;width:100%;height:100%;
                                    object-fit:cover}
              #minecraft-background:not([src]){display:none}
              .desktop{position:relative;height:100%;padding:48px;
                       display:grid;gap:24px;
                       grid-template-columns:repeat(2,minmax(0,1fr));
                       grid-template-rows:repeat(2,minmax(0,1fr));
                       box-sizing:border-box}
              .glass{background:rgba(20,20,30,0.35);
                     border:1px solid rgba(255,255,255,0.15);
                     border-radius:14px;padding:20px;
                     box-shadow:0 8px 32px rgba(0,0,0,0.35)}
              .g1{backdrop-filter:blur(18px)}
              .g2{backdrop-filter:blur(18px) saturate(140%)}
              .g3{backdrop-filter:blur(40px) saturate(120%)}
              .g4{background:rgba(20,20,30,0.35)}
              h2{margin:0 0 8px;font-size:16px;font-weight:600}
              p{margin:0;font-size:13px;color:#b9bfd0}
              code{font-family:Consolas,monospace;color:#7dd3a0}
            </style></head><body>
              <img id="minecraft-background" alt="">
              <main class="desktop">
                <section class="glass g1"><h2>Nur Weichzeichner</h2>
                  <p><code>blur(18px)</code></p></section>
                <section class="glass g2"><h2>Weichzeichner und Sättigung</h2>
                  <p><code>blur(18px) saturate(140%)</code></p></section>
                <section class="glass g3"><h2>Starker Weichzeichner</h2>
                  <p><code>blur(40px) saturate(120%)</code></p></section>
                <section class="glass g4"><h2>Halbdurchsichtig ohne Filter</h2>
                  <p>Zum Vergleich — hier wird nichts gefiltert.</p></section>
              </main>
            <script>
              // Der einzige Zweck: die Adresse von außen setzbar machen, ohne
              // die Seite neu zu laden. Kein Nachfragen, keine Rückrichtung.
              var bg = document.getElementById('minecraft-background');
              // Ein Bild, das nicht lädt, sagt von sich aus nichts — Chromium
              // schreibt dafür keine Zeile in seine Konsole. Ohne diese
              // Meldung bleibt als einziger Hinweis ein Bruchsymbol in der
              // Ecke, und danach sucht man lange.
              bg.addEventListener('error', function () {
                console.error('Hintergrund lud nicht: ' + bg.getAttribute('src'));
              });
              console.log('Seite bereit');
              window.fnSetBackground = function (url) {
                console.log('Hintergrund gesetzt: ' + url);
                bg.src = url;
              };
            </script></body></html>
            """;

    /**
     * Not created ourselves, but the one that Chromium's registration hangs
     * off. One of our own would never have been queried.
     */
    private final FrameStore store = FrameSchemes.store();
    private final WorldCapture capture = new WorldCapture();

    private Mode mode;
    private double scale;
    private WorldCapture.Format format;

    private boolean schemeReady;
    private long lastCaptureNanos;
    private long captures;

    protected BackdropScreen(Component title, String url, Mode mode,
                             double scale, WorldCapture.Format format) {
        super(title, url, false);
        this.mode = mode;
        this.scale = scale;
        this.format = format;
    }

    /** Writes the page out and opens the screen. */
    public static void open(Minecraft client, Mode mode, double scale,
                            WorldCapture.Format format) throws Exception {
        Path file = Files.createTempFile("fn-backdrop-screen", ".html");
        Files.writeString(file, PAGE, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        client.setScreen(new BackdropScreen(Component.literal("Hintergrund"),
                file.toUri().toString(), mode, scale, format));
    }

    @Override
    protected void init() {
        super.init();
        if (!schemeReady) {
            schemeReady = FrameSchemes.register();
            if (!schemeReady) {
                LOG.warn("Chromium hat das Schema nicht angenommen — kein Hintergrund");
            }
        }
        // <b>After a resize the old backdrop is wrong.</b> It would have the
        // aspect ratio from before, and object-fit would crop or stretch it.
        // The next pass captures anew.
        lastCaptureNanos = 0;
    }

    /**
     * Captures when it is time.
     *
     * <p>The only point in the whole sequence at which Minecraft's image still
     * stands untouched in the buffer — and at which our own browser is not yet
     * in it.
     */
    @Override
    protected void beforeDrawing() {
        if (!schemeReady || !hasSession()) {
            return;
        }
        // <b>Not while the page is loading.</b> The swap goes via a call into
        // the document, and before loading finishes this document does not
        // exist. The call would then fizzle out — no error, no trace — and for
        // a still image there would be no second attempt. That is exactly how
        // it first turned out here: an empty frame and glass panes filtering
        // black.
        if (pageLoading()) {
            return;
        }
        long now = System.nanoTime();
        boolean first = lastCaptureNanos == 0;
        if (!first && now - lastCaptureNanos < mode.intervalNanos()) {
            return;
        }
        lastCaptureNanos = now;
        long generation = capture.captureInto(store, scale, format);
        if (generation == 0) {
            return;
        }
        captures++;
        showBackground(generation);
    }

    /**
     * Sets the image's address anew.
     *
     * <p>The generation in the address is what makes Chromium reload: without
     * it, it would consider the address the same and keep showing its cached
     * image. The handler additionally sends a {@code no-store} — which of the
     * two means suffices on its own is worth a measurement of its own.
     */
    private void showBackground(long generation) {
        String script = "window.fnSetBackground && window.fnSetBackground('"
                + backgroundUrlFor(generation) + "');";
        runScript(script);
    }

    /**
     * Which address is set for this image.
     *
     * <p>Overridable, so that what happens without the generation can be
     * checked — that is the question about Chromium's cache, and it answers
     * itself only by taking the generation away.
     */
    protected String backgroundUrlFor(long generation) {
        return FrameSchemes.urlFor(generation);
    }

    public long captures() {
        return captures;
    }

    public WorldCapture capture() {
        return capture;
    }

    public FrameStore store() {
        return store;
    }

    protected void setMode(Mode next) {
        this.mode = next;
        this.lastCaptureNanos = 0;
    }

    protected void setScale(double next) {
        this.scale = next;
        this.lastCaptureNanos = 0;
    }

    protected void setFormat(WorldCapture.Format next) {
        this.format = next;
        this.lastCaptureNanos = 0;
    }

    protected Mode mode() {
        return mode;
    }

    protected double scale() {
        return scale;
    }

    protected WorldCapture.Format format() {
        return format;
    }

    @Override
    public void removed() {
        super.removed();
        capture.close();
        // <b>The store is not cleared.</b> It belongs to the session and not
        // to this screen; clearing it here would take the next one's image
        // away, without the registration knowing about it.
    }
}
