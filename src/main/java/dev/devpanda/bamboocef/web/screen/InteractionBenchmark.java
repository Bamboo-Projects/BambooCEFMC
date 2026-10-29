package dev.devpanda.bamboocef.web.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Measures what ordinary use costs — and uses itself to do it.
 *
 * <p><b>The question is not whether the frame path is fast.</b> That is
 * measured: a resting editor costs nothing, a blinking cursor 2.3 KB per
 * frame, a full-surface animation 240 MB per second. The open question is
 * where between these extremes <b>use</b> lies. A scroll that repaints the
 * whole page every time costs as much as an animation — and one would not see
 * it.
 *
 * <p><b>Why the events go through the screen and not straight to the
 * session.</b> What is to be measured is the route a player takes: conversion
 * from GUI units to browser pixels, focus check, click counting. Whoever fed
 * the session directly would bypass exactly the parts where a fault would sit.
 *
 * <p>Each section runs for a few seconds, then its line stands in the log and
 * the counters start over.
 */
public final class InteractionBenchmark extends BrowserScreen {

    private static final Logger LOG =
            LoggerFactory.getLogger("BambooCEF/InteractionBenchmark");

    /**
     * How long a section lasts.
     *
     * <p>In time and not in frames: the frame rate fluctuates, and sections of
     * different length could not be compared. On the first attempt "Maus
     * bewegen" ran 2.2 seconds and "Rollen" four.
     */
    private static final long SECTION_NANOS = 4_000_000_000L;

    /** Rest at the start, so that the page has finished loading. */
    private static final long SETTLE_NANOS = 2_000_000_000L;

    /**
     * A page with fixed positions.
     *
     * <p>The probe page for people centres its content and is thus unusable
     * for a measurement: where a click has to land would depend on the window
     * size. Here everything sits at fixed fractions of the surface.
     */
    private static final String PAGE = """
            <!doctype html><html lang="de"><head><meta charset="utf-8"><style>
              html,body{margin:0;height:100%;background:#12131a;color:#e6e8f0;
                        font:15px/1.5 system-ui,sans-serif;overflow:hidden}
              #schreiben{position:fixed;left:5%;top:8%;width:40%;height:16%;
                         background:#0d0e14;color:#e6e8f0;border:1px solid #3a3d4d;
                         padding:8px;font:14px/1.4 Consolas,monospace;resize:none}
              #waehlen{position:fixed;left:55%;top:8%;width:35%;
                       background:#0d0e14;color:#e6e8f0;border:1px solid #3a3d4d;
                       padding:8px}
              #rollen{position:fixed;left:5%;top:32%;width:85%;height:60%;
                      overflow-y:scroll;border:1px solid #3a3d4d}
              #rollen div{padding:6px 10px;border-bottom:1px solid #242632}
              /* Ohne Hover kostet eine Mausbewegung nichts — Chromium malt
                 nur, was sich ändert. Eine Messung ohne Hover misst damit
                 nicht den Editor, sondern eine Postkarte. Jede Zeile, jedes
                 Feld reagiert deshalb auf den Zeiger, wie es ein Editor tut:
                 Zeilenhervorhebung, Feldrahmen, Knopfzustände. */
              #rollen div:hover{background:#242632;color:#7dd3a0}
              #schreiben:hover,#waehlen:hover{border-color:#7dd3a0}
              #glas:hover{background:rgba(125,211,160,0.55)}
              #glas{position:fixed;right:2%;bottom:2%;width:20%;height:12%;
                    background:rgba(125,211,160,0.35);border:1px solid #7dd3a0}
            </style></head><body>
              <textarea id="schreiben" spellcheck="false"></textarea>
              <select id="waehlen">
                <option>Kupferdraht</option><option>Siliziumwafer</option>
                <option>Prozessorkern</option><option>Speicherzelle</option>
                <option>Signalverstärker</option><option>Netzwerkkarte</option>
              </select>
              <div id="rollen"></div>
              <div id="glas"></div>
            <script>
              var r = document.getElementById('rollen');
              for (var i = 1; i <= 200; i++) {
                var d = document.createElement('div');
                d.textContent = 'Zeile ' + i + ' — Inhalt zum Rollen';
                r.appendChild(d);
              }
            </script></body></html>
            """;

    /** The sections, in this order. */
    private enum Step {
        SETTLE("Aufbau"),
        STILL("Ruhe — nichts tun"),
        MOUSE("Maus bewegen"),
        SCROLL("Rollen"),
        TYPE("Tippen ins Textfeld"),
        SPECIAL("Sondertasten"),
        PASTE("Einfügen aus der Zwischenablage"),
        SELECT("Auswahlfeld offen"),
        RESIZE("Größe ändern"),
        DONE("fertig");

        final String label;

        Step(String label) {
            this.label = label;
        }
    }

    private static final String TYPED = "Umlaute äöü ÄÖÜ ß, Zeichen € {} [] <> und Text. ";

    private Step step = Step.SETTLE;
    private long stepStartedNanos;
    private int framesInStep;
    private int typedIndex;

    private InteractionBenchmark(String url) {
        super(Component.literal("Interaktionsmessung"), url, false);
    }

    /** Writes the measurement page out and opens the measurement. */
    public static void open(Minecraft client) throws Exception {
        Path file = Files.createTempFile("fn-interaction", ".html");
        Files.writeString(file, PAGE, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        client.setScreen(new InteractionBenchmark(file.toUri().toString()));
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                       int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        advance();
    }

    private void advance() {
        if (step == Step.DONE || !hasSession()) {
            return;
        }
        if (stepStartedNanos == 0) {
            stepStartedNanos = System.nanoTime();
        }
        framesInStep++;
        act();
        long limit = step == Step.SETTLE ? SETTLE_NANOS : SECTION_NANOS;
        if (System.nanoTime() - stepStartedNanos < limit) {
            return;
        }
        // <b>Report after the settle too, even though there is nothing to
        // report.</b> The call resets the counters and remembers the moment;
        // without it the first real section carries the numbers of the settle
        // and all labels are off by one. That is exactly how it first turned
        // out here.
        report();
        cleanUpStep();
        step = Step.values()[step.ordinal() + 1];
        framesInStep = 0;
        stepStartedNanos = System.nanoTime();
        beginStep();
        if (step == Step.DONE) {
            LOG.info("Interaktionsmessung fertig.");
            onClose();
        }
    }

    /** What is done in this section on every frame. */
    private void act() {
        switch (step) {
            case MOUSE -> {
                // A circle across the surface: now over text, now over a
                // button, now over nothing. That is closer to real use than a
                // straight line.
                double angle = framesInStep * 0.1;
                double x = width * (0.5 + 0.35 * Math.cos(angle));
                double y = height * (0.5 + 0.35 * Math.sin(angle));
                mouseMoved(x, y);
            }
            case SCROLL -> {
                if (framesInStep % 6 == 0) {
                    mouseScrolled(width * 0.5, height * 0.6, 0.0, -1.0);
                }
            }
            case TYPE -> {
                // About five characters per second — fast, but human typing.
                // Each character separately, as it would come from the
                // keyboard.
                if (framesInStep % 12 == 0) {
                    char next = TYPED.charAt(typedIndex % TYPED.length());
                    typedIndex++;
                    charTyped(new net.minecraft.client.input.CharacterEvent(next));
                }
            }
            default -> {
            }
        }
    }

    /** What happens once at the start of a section. */
    private void beginStep() {
        switch (step) {
            case TYPE -> {
                // Click into the text field, otherwise the text lands nowhere.
                clickAt(0.25, 0.16);
            }
            case SPECIAL -> {
                // <b>Exactly the keys for which the contract is unusual.</b>
                // On press and release this JCEF fork expects the GLFW key code
                // in keyChar, because it compares it with GLFW_KEY_LEFT and
                // relatives to choose the right scancode. Were a character
                // there, arrow keys would get the scancode of some letter — and
                // nothing would happen. If something does happen, the text
                // field changes, and a change is a frame.
                clickAt(0.25, 0.16);
                for (int key : new int[] {
                        GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END,
                        GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
                        GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_BACKSPACE,
                        GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE}) {
                    sendPlain(key);
                }
            }
            case PASTE -> {
                // <b>What can be checked here and what cannot.</b> Whether the
                // text really arrives only the page itself would know — and a
                // bridge to it does not exist yet. What can be checked is
                // whether anything happens at all: a paste that arrives changes
                // the text field, and a change is a frame. If it stays at zero
                // frames, the route is dead.
                // Enough text that a paste cannot be mistaken for a blinking
                // cursor: that changes two kilobytes, a filled text field a
                // multiple of it.
                minecraft.keyboardHandler.setClipboard(
                        ("Aus der Zwischenablage: äöü ÄÖÜ ß € {} [] <> ").repeat(6));
                clickAt(0.25, 0.16);
                sendCombination(GLFW.GLFW_KEY_A);        // select all
                sendCombination(GLFW.GLFW_KEY_V);        // and replace
            }
            case SELECT -> {
                // Open the select box — the only simple way to a real Chromium
                // popup.
                clickAt(0.72, 0.115);
            }
            case RESIZE -> {
                // Make the surface narrower and back again. Chromium recomputes
                // the whole page in doing so; the question is what that costs
                // in transfer.
                resizeTo(0.7);
            }
            default -> {
            }
        }
    }

    private void cleanUpStep() {
        switch (step) {
            case SELECT -> {
                // Escape closes the box — and because Escape belongs to the
                // page here, that works without a detour.
                keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                keyReleased(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            }
            case RESIZE -> resizeTo(1.0);
            default -> {
            }
        }
    }

    /**
     * A key with a modifier, as it would come from a keyboard.
     *
     * <p>The Ctrl modifier goes along as a GLFW flag; JCEF's native part turns
     * it into {@code EVENTFLAG_CONTROL_DOWN}, and Chromium does the rest itself
     * — it has its own path to the operating system's clipboard. There would
     * be nothing to rebuild about that.
     *
     * <p><b>The scancode is not optional on Windows.</b> The native part builds
     * the Windows key code with {@code MapVirtualKey} <b>from the scancode</b>;
     * a zero in it yields a key code of zero, and Chromium sees a key that does
     * not exist. On real input Minecraft supplies the scancode — here it has to
     * be queried.
     *
     * <p>This was no trifle: on the first attempt Ctrl+A and Ctrl+V arrived
     * nowhere, and the measurement showed only the blinking cursor.
     */
    private void sendCombination(int glfwKey) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(new net.minecraft.client.input.KeyEvent(glfwKey, scanCode, GLFW.GLFW_MOD_CONTROL));
        keyReleased(new net.minecraft.client.input.KeyEvent(glfwKey, scanCode, GLFW.GLFW_MOD_CONTROL));
    }

    /** A key without a modifier, likewise with a real scancode. */
    private void sendPlain(int glfwKey) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(new net.minecraft.client.input.KeyEvent(glfwKey, scanCode, 0));
        keyReleased(new net.minecraft.client.input.KeyEvent(glfwKey, scanCode, 0));
    }

    private void clickAt(double acrossFraction, double downFraction) {
        double x = width * acrossFraction;
        double y = height * downFraction;
        mouseMoved(x, y);
        var press = new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(0, 0));
        mouseClicked(press, false);
        mouseReleased(press);
    }

    private void report() {
        LOG.info("--- {} ---", step.label);
        reportSection(step.label);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Just as a safeguard: the run should not be disturbed by hand. */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && step != Step.SELECT) {
            LOG.info(String.format(Locale.GERMANY,
                    "Interaktionsmessung bei „%s\" abgebrochen", step.label));
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
