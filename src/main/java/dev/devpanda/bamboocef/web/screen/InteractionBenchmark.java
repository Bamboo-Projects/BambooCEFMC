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
 * Misst, was gewöhnliches Bedienen kostet — und bedient sich dafür selbst.
 *
 * <p><b>Die Frage ist nicht, ob der Bildweg schnell ist.</b> Das ist gemessen:
 * ein ruhender Editor kostet nichts, ein blinkender Cursor 2,3 KB je Bild, eine
 * vollflächige Animation 240 MB je Sekunde. Die offene Frage ist, wo zwischen
 * diesen Enden das <b>Bedienen</b> liegt. Ein Scrollen, das jedes Mal die ganze
 * Seite neu malt, kostet so viel wie eine Animation — und man sähe es nicht.
 *
 * <p><b>Warum die Ereignisse durch den Bildschirm gehen und nicht direkt an die
 * Sitzung.</b> Gemessen werden soll der Weg, den ein Spieler nimmt: Umrechnung
 * von GUI-Einheiten in Browser-Pixel, Fokusprüfung, Klickzählung. Wer die
 * Sitzung direkt fütterte, umginge genau die Teile, an denen ein Fehler säße.
 *
 * <p>Jeder Abschnitt läuft ein paar Sekunden, dann steht seine Zeile im
 * Protokoll und die Zähler beginnen von vorn.
 */
public final class InteractionBenchmark extends BrowserScreen {

    private static final Logger LOG =
            LoggerFactory.getLogger("FactoryNetwork/InteractionBenchmark");

    /**
     * Wie lange ein Abschnitt dauert.
     *
     * <p>In Zeit und nicht in Bildern: Die Bildrate schwankt, und Abschnitte
     * von verschiedener Länge ließen sich nicht vergleichen. Beim ersten
     * Versuch lief „Maus bewegen" 2,2 Sekunden und „Rollen" vier.
     */
    private static final long SECTION_NANOS = 4_000_000_000L;

    /** Ruhe zu Beginn, damit die Seite fertig geladen ist. */
    private static final long SETTLE_NANOS = 2_000_000_000L;

    /**
     * Eine Seite mit festen Plätzen.
     *
     * <p>Die Prüfseite für Menschen zentriert ihren Inhalt und ist damit für
     * eine Messung unbrauchbar: Wohin geklickt werden muss, hinge von der
     * Fenstergröße ab. Hier steht alles an festen Anteilen der Fläche.
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

    /** Die Abschnitte, in dieser Reihenfolge. */
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

    /** Legt die Messseite ab und öffnet die Messung. */
    public static void open(Minecraft client) throws Exception {
        Path file = Files.createTempFile("fn-interaction", ".html");
        Files.writeString(file, PAGE, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        client.setScreen(new InteractionBenchmark(file.toUri().toString()));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics,
                       int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
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
        // <b>Auch nach dem Aufbau melden, obwohl nichts zu melden ist.</b> Der
        // Aufruf setzt die Zähler zurück und merkt sich den Zeitpunkt; ohne ihn
        // trägt der erste echte Abschnitt die Zahlen des Aufbaus und alle
        // Beschriftungen sind um eins verschoben. Genau so ist es hier zuerst
        // ausgegangen.
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

    /** Was in diesem Abschnitt bei jedem Bild getan wird. */
    private void act() {
        switch (step) {
            case MOUSE -> {
                // Ein Kreis über die Fläche: mal über Text, mal über einen
                // Knopf, mal über nichts. Das ist näher am wirklichen Bedienen
                // als eine gerade Linie.
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
                // Etwa fünf Zeichen je Sekunde — schnelles, aber menschliches
                // Tippen. Jedes Zeichen einzeln, wie es von der Tastatur käme.
                if (framesInStep % 12 == 0) {
                    char next = TYPED.charAt(typedIndex % TYPED.length());
                    typedIndex++;
                    charTyped(next, 0);
                }
            }
            default -> {
            }
        }
    }

    /** Was zu Beginn eines Abschnitts einmal geschieht. */
    private void beginStep() {
        switch (step) {
            case TYPE -> {
                // Ins Textfeld klicken, sonst landet der Text nirgends.
                clickAt(0.25, 0.16);
            }
            case SPECIAL -> {
                // <b>Genau die Tasten, für die der Vertrag ungewöhnlich ist.</b>
                // Bei Druck und Loslassen erwartet dieser JCEF-Fork in keyChar
                // den GLFW-Tastencode, weil er ihn mit GLFW_KEY_LEFT und
                // Verwandten vergleicht, um den richtigen Scancode zu wählen.
                // Wäre dort ein Schriftzeichen, bekämen Pfeiltasten den
                // Scancode irgendeines Buchstabens — und nichts geschähe.
                // Geschieht doch etwas, ändert sich das Textfeld, und eine
                // Änderung ist ein Bild.
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
                // <b>Was hier geprüft werden kann und was nicht.</b> Ob der
                // Text wirklich ankommt, wüsste nur die Seite selbst — und
                // eine Brücke zu ihr gibt es noch nicht. Prüfbar ist, ob
                // überhaupt etwas geschieht: Ein Einfügen, das ankommt, ändert
                // das Textfeld, und eine Änderung ist ein Bild. Bleibt es bei
                // null Bildern, ist der Weg tot.
                // Genug Text, dass ein Einfügen sich nicht mit einem
                // blinkenden Cursor verwechseln lässt: Der ändert zwei
                // Kilobyte, ein gefülltes Textfeld ein Vielfaches davon.
                minecraft.keyboardHandler.setClipboard(
                        ("Aus der Zwischenablage: äöü ÄÖÜ ß € {} [] <> ").repeat(6));
                clickAt(0.25, 0.16);
                sendCombination(GLFW.GLFW_KEY_A);        // alles markieren
                sendCombination(GLFW.GLFW_KEY_V);        // und ersetzen
            }
            case SELECT -> {
                // Das Auswahlfeld aufklappen — der einzige einfache Weg zu
                // einem echten Popup von Chromium.
                clickAt(0.72, 0.115);
            }
            case RESIZE -> {
                // Die Fläche schmaler machen und wieder zurück. Chromium
                // rechnet dabei die ganze Seite neu; die Frage ist, was das
                // an Übertragung kostet.
                resizeTo(0.7);
            }
            default -> {
            }
        }
    }

    private void cleanUpStep() {
        switch (step) {
            case SELECT -> {
                // Escape schließt das Feld — und weil Escape hier der Seite
                // gehört, geht das ohne Umweg.
                keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                keyReleased(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case RESIZE -> resizeTo(1.0);
            default -> {
            }
        }
    }

    /**
     * Eine Taste mit Steuerung, wie sie von einer Tastatur käme.
     *
     * <p>Der Steuerungs-Modifikator geht als GLFW-Flagge mit; der native Teil
     * von JCEF macht daraus {@code EVENTFLAG_CONTROL_DOWN}, und den Rest
     * erledigt Chromium selbst — es hat einen eigenen Weg zur Zwischenablage
     * des Betriebssystems. Nachzubauen wäre daran nichts.
     *
     * <p><b>Der Scancode ist auf Windows nicht optional.</b> Der native Teil
     * baut den Windows-Tastencode mit {@code MapVirtualKey} <b>aus dem
     * Scancode</b>; eine Null darin ergibt einen Tastencode von null, und
     * Chromium sieht eine Taste, die es nicht gibt. Bei echter Eingabe liefert
     * Minecraft den Scancode mit — hier muss er erfragt werden.
     *
     * <p>Das ist keine Kleinigkeit gewesen: Beim ersten Versuch kam Strg+A und
     * Strg+V nirgends an, und die Messung zeigte nur den blinkenden Cursor.
     */
    private void sendCombination(int glfwKey) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(glfwKey, scanCode, GLFW.GLFW_MOD_CONTROL);
        keyReleased(glfwKey, scanCode, GLFW.GLFW_MOD_CONTROL);
    }

    /** Eine Taste ohne Zusatz, ebenfalls mit echtem Scancode. */
    private void sendPlain(int glfwKey) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(glfwKey, scanCode, 0);
        keyReleased(glfwKey, scanCode, 0);
    }

    private void clickAt(double acrossFraction, double downFraction) {
        double x = width * acrossFraction;
        double y = height * downFraction;
        mouseMoved(x, y);
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    private void report() {
        LOG.info("--- {} ---", step.label);
        reportSection(step.label);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Nur zur Sicherheit: Der Ablauf soll nicht von Hand gestört werden. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && step != Step.SELECT) {
            LOG.info(String.format(Locale.GERMANY,
                    "Interaktionsmessung bei „%s\" abgebrochen", step.label));
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
