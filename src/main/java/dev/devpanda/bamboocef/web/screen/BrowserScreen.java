package dev.devpanda.bamboocef.web.screen;

import dev.devpanda.bamboocef.web.BrowserVisibility;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.input.BrowserFocus;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;
import dev.devpanda.bamboocef.web.view.BrowserCompositor;
import dev.devpanda.bamboocef.web.view.BrowserCursor;
import dev.devpanda.bamboocef.web.view.BrowserView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ein Bildschirm, der eine Webseite zeigt und bedienbar macht.
 *
 * <p><b>Zur Escape-Taste, und warum sie nicht Minecraft gehört.</b> Die
 * bequeme Annahme wäre: Escape schließt den Bildschirm, wie überall in
 * Minecraft. Für eine Weboberfläche ist das falsch — ein aufgeklapptes
 * Auswahlfeld schließt sich mit Escape, ein Dialog ebenso, und ein Editor
 * bricht damit seine Vervollständigung ab. Wer Escape abfängt, nimmt der Seite
 * eine Taste, die sie selbst braucht, und der Spieler merkt nur, dass „das
 * Menü sich nicht schließen lässt".
 *
 * <p>Die Entscheidung: <b>Escape geht an die Seite.</b> Für den Rückweg gibt
 * es {@link #RELEASE_KEY} — sie gibt den Fokus an Minecraft zurück, und dann
 * schließt Escape wie gewohnt. Zwei Zustände statt einer Sonderregel, und
 * dasselbe Modell trägt später eine Fläche in der Welt: Auch die bekommt den
 * Fokus und gibt ihn wieder her.
 *
 * <p><b>Aufgeräumt wird in {@code removed()}, nicht in {@code onClose()}.</b>
 * Wer einen anderen Bildschirm öffnet, während dieser läuft, bekommt kein
 * {@code onClose()} — der Browser bliebe offen, unsichtbar, und malte weiter.
 * {@code removed()} kommt in jedem Fall.
 *
 * <p><b>Das Schließen betrifft nur diesen Browser.</b> Chromium selbst bleibt
 * stehen — und das gilt weiter, seit es uns gehört: Andere Browser derselben
 * Mod hängen daran, und Hochfahren kostet Sekunden. Abgeräumt wird beim
 * Verlassen des Spiels, an einer Stelle, nicht bei jedem geschlossenen
 * Bildschirm.
 */
public class BrowserScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/BrowserScreen");

    /**
     * Die Taste, die den Fokus zurückholt.
     *
     * <p>F10, weil Vanilla sie nicht belegt und Weboberflächen sie selten
     * brauchen. Escape wäre die naheliegende Wahl und ist genau deshalb keine.
     */
    public static final int RELEASE_KEY = GLFW.GLFW_KEY_F10;

    /**
     * Die Taste, die den Messabschnitt abschließt.
     *
     * <p>Sie schreibt ins Protokoll, was seit dem letzten Druck an Bildern und
     * Bytes angefallen ist, und fängt von vorn an. So lässt sich eine einzelne
     * Handlung messen — nur scrollen, nur tippen — ohne dass sich die
     * Abschnitte vermischen.
     */
    public static final int MEASURE_KEY = GLFW.GLFW_KEY_F9;

    /** Wie lange der Hinweis auf die Rückholtaste stehen bleibt. */
    private static final long HINT_MILLIS = 6000L;

    private final String url;
    private final boolean transparent;

    private BrowserSession session;
    private BrowserView view;
    private BrowserFocus focus = BrowserFocus.BROWSER;
    private final BrowserCompositor compositor = new BrowserCompositor();
    private final BrowserCursor cursor = new BrowserCursor();

    /**
     * Der zuletzt gewünschte Zeiger, gemerkt statt sofort gesetzt.
     *
     * <p>Chromium meldet Zeigerwechsel auch aus eigenen Threads. GLFW verträgt
     * Fensteraufrufe nur aus dem Render-Thread; gemerkt und beim Zeichnen
     * angewandt ist der Weg ohne Absturz.
     */
    private volatile int wantedCursor = -1;
    private int appliedCursor = -1;

    /** Welche Zeigerarten schon einmal gemeldet wurden — je Art eine Zeile. */
    private final java.util.Set<Integer> seenCursors = new java.util.HashSet<>();

    private long openedMillis;
    private boolean insideLastFrame;
    private long measuringSinceNanos;
    private long paintsAtMeasureStart;
    private long popupPaintsAtMeasureStart;
    private int measureSection;

    public BrowserScreen(Component title, String url, boolean transparent) {
        super(title);
        this.url = url;
        this.transparent = transparent;
    }

    /** Ob überhaupt ein Browser zu haben ist — vor dem Öffnen zu fragen. */
    public static WebRuntimeStatus availability() {
        return WebSupport.ensureStarted();
    }

    @Override
    protected void init() {
        super.init();
        openedMillis = System.currentTimeMillis();
        view = BrowserView.fullscreen(width, height, minecraft.getWindow().getGuiScale());
        if (session == null) {
            WebRuntimeStatus status = WebSupport.ensureStarted();
            if (!status.usable()) {
                LOG.warn("Kein Browser zu haben: {}", status);
                return;
            }
            session = BrowserSession.open(url, transparent,
                    view.browserWidth(), view.browserHeight(),
                    BrowserVisibility.FOREGROUND);
            session.onCursor(type -> wantedCursor = type);
            session.setFocused(true);
        } else {
            // <b>Hier hängt die Größenänderung.</b> Minecraft ruft bei jeder
            // Fenster- oder Skalierungsänderung {@code resize()}, und das ruft
            // {@code init()} erneut. Eine eigene Überschreibung von
            // {@code resize()} bräuchte es nur, um dasselbe noch einmal zu
            // tun. Der Browser bleibt stehen und bekommt ein neues Maß.
            session.resize(view.browserWidth(), view.browserHeight());
        }
    }

    /**
     * Läuft, bevor irgendetwas gezeichnet wird.
     *
     * <p><b>Die einzige Stelle, an der Minecrafts eigenes Bild noch
     * unberührt im Puffer steht.</b> Welt und Bedienoberfläche sind fertig,
     * dieser Bildschirm hat noch keinen Strich getan. Wer das Bild
     * aufnehmen will, muss es hier tun — eine Zeile später hat
     * {@code renderBackground} es verdunkelt, und ein Bild später läge der
     * Browser darin.
     */
    protected void beforeDrawing() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (session != null) {
            session.noteScreenFrame();
        }
        beforeDrawing();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (session == null || view == null) {
            graphics.drawCenteredString(font,
                    Component.literal("Die Web-Runtime steht nicht bereit."),
                    width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        applyWantedCursor();
        compositor.draw(graphics, view, session.textureId(), popupPlacement());
        drawHint(graphics);
    }

    /**
     * Wo das aufgeklappte Feld auf dem Schirm liegt.
     *
     * <p>Über dieselbe Umrechnung wie die Maus: Chromium meldet die Lage in
     * seinen eigenen Pixeln, und wer sie anders umrechnet als den Mauszeiger,
     * zeichnet das Feld neben die Stelle, an der es angeklickt wird.
     */
    private BrowserCompositor.PopupPlacement popupPlacement() {
        if (!session.popupVisible()) {
            return null;
        }
        double[] corner = view.toGui(session.popupX(), session.popupY());
        return new BrowserCompositor.PopupPlacement(true, session.popupTextureId(),
                corner[0], corner[1],
                view.lengthToGui(session.popupWidth()),
                view.lengthToGui(session.popupHeight()));
    }

    private void applyWantedCursor() {
        int wanted = wantedCursor;
        if (wanted != appliedCursor && focus == BrowserFocus.BROWSER) {
            cursor.apply(minecraft.getWindow().getWindow(), wanted);
            appliedCursor = wanted;
            // Jede Art einmal ins Protokoll: Ob die Zeigerwechsel überhaupt
            // ankommen, sieht man sonst nur, indem man hinsieht — und
            // „hinsehen" ist die Antwort, die dieser ganze Spike vermeidet.
            //
            // <b>Mit -Ptrace jeden Wechsel, nicht nur jede neue Art.</b> Ein
            // Zeiger, der an der falschen Stelle steht, lässt sich sonst nicht
            // von einem unterscheiden, der nur nicht zurückgesetzt wurde: Die
            // zweite Anforderung derselben Art stünde nirgends.
            boolean firstOfKind = seenCursors.add(wanted);
            if (firstOfKind || Boolean.getBoolean("fn.cef.trace")) {
                LOG.info("Mauszeiger: Chromium wünscht {} ({})",
                        dev.devpanda.bamboocef.web.view.CursorType.fromId(wanted), wanted);
            }
        }
    }

    /**
     * Der Hinweis auf die Rückholtaste, für die ersten Sekunden.
     *
     * <p>Eine Taste, die man kennen muss und nirgends steht, ist keine
     * Lösung — sie ist eine Falle. Nach ein paar Sekunden verschwindet der
     * Hinweis, weil er dann nur noch im Weg wäre.
     */
    private void drawHint(GuiGraphics graphics) {
        long shown = System.currentTimeMillis() - openedMillis;
        if (shown > HINT_MILLIS) {
            return;
        }
        String text = focus == BrowserFocus.BROWSER
                ? "F10 gibt die Tastatur an Minecraft zurück · F9 misst einen Abschnitt"
                : "F10 gibt die Tastatur an die Seite — Escape schließt";
        int alpha = (int) (255 * Math.min(1.0, (HINT_MILLIS - shown) / 1000.0));
        graphics.fill(0, 0, width, 14, (alpha / 2) << 24);
        graphics.drawString(font, text, 6, 3, (alpha << 24) | 0xFFFFFF, false);
    }

    // ---- Fokus -------------------------------------------------------------

    /**
     * Escape schließt nicht von selbst.
     *
     * <p>Solange die Seite den Fokus hat, gehört das erste Escape ihr; ein
     * zweites unmittelbar danach schließt. Ist der Fokus bei Minecraft, wird
     * Escape in {@link #keyPressed} von Hand behandelt. Minecrafts eigene
     * Behandlung würde beim ersten Escape schließen und der Seite nichts
     * lassen.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * Das Spiel läuft weiter.
     *
     * <p>Ein Editor ist kein Pausenmenü: Wer ein Programm schreibt, während
     * eine Maschine läuft, will sie laufen sehen.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setFocus(BrowserFocus wanted) {
        if (focus == wanted) {
            return;
        }
        focus = wanted;
        openedMillis = System.currentTimeMillis();      // Hinweis wieder zeigen
        if (session != null) {
            session.setFocused(wanted == BrowserFocus.BROWSER);
        }
        if (wanted == BrowserFocus.MINECRAFT) {
            cursor.reset(minecraft.getWindow().getWindow());
            appliedCursor = -1;
        }
    }

    // ---- Tastatur ----------------------------------------------------------

    /**
     * Ob das letzte Ereignis ein Escape war.
     *
     * <p><b>Warum es das braucht.</b> Escape gehört zwei Empfängern: Monaco
     * schließt damit seine Vorschlagsliste, sein Suchfeld, seine Übersicht —
     * und ein Minecraft-Bildschirm schließt sich damit. Beides gleichzeitig
     * geht nicht, und wer den Fokus hat, bekommt sonst alles.
     *
     * <p>Bisher bekam die Seite jedes Escape, und der Zweig, der schließt, war
     * nie erreichbar. Jetzt gilt: <b>Das erste Escape gehört der Seite, ein
     * zweites unmittelbar danach schließt den Bildschirm.</b> Jede andere
     * Eingabe dazwischen — Taste oder Mausklick — setzt den Zähler zurück,
     * denn dann hat der Benutzer weitergearbeitet und meinte kein Schließen.
     *
     * <p>Java kann nicht wissen, ob Monaco gerade etwas offen hat. Diese Regel
     * braucht es auch nicht: Wer etwas geschlossen hat, drückt nicht sofort
     * noch einmal.
     */
    private boolean escapePending;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == RELEASE_KEY) {
            setFocus(focus == BrowserFocus.BROWSER
                    ? BrowserFocus.MINECRAFT : BrowserFocus.BROWSER);
            return true;
        }
        if (keyCode == MEASURE_KEY) {
            reportAndResetMeasurement();
            return true;
        }
        if (session != null && focus.routesKeyboard()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (escapePending) {
                    // Das zweite Escape. Das erste hatte die Seite; wenn sie
                    // damit etwas geschlossen hat, hätte der Benutzer danach
                    // weitergearbeitet und nicht noch einmal Escape gedrückt.
                    escapePending = false;
                    onClose();
                    return true;
                }
                escapePending = true;
            } else {
                escapePending = false;
            }
            session.keyPressed(keyCode, scanCode, modifiers);
            // <b>Nicht an super weitergeben.</b> Sonst sieht der Bildschirm
            // dieselbe Taste noch einmal, und was die Seite gerade
            // entgegengenommen hat, löst zusätzlich etwas in Minecraft aus.
            return true;
        }
        escapePending = false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == RELEASE_KEY) {
            return true;
        }
        if (session != null && focus.routesKeyboard()) {
            session.keyReleased(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * Getippter Text.
     *
     * <p>Der einzige Weg, auf dem Umlaute, ß und das Eurozeichen richtig
     * ankommen: Minecraft hat das Zeichen vom Betriebssystem bekommen,
     * einschließlich Tastaturbelegung. Aus Tastencodes ließe es sich nicht
     * zurückrechnen.
     */
    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (typed == 0) {
            return false;
        }
        if (session != null && focus.routesKeyboard()) {
            session.charTyped(typed, modifiers);
            return true;
        }
        return super.charTyped(typed, modifiers);
    }

    // ---- Maus --------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (session == null || view == null) {
            return;
        }
        boolean inside = view.contains(mouseX, mouseY);
        if (inside) {
            session.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                    heldModifiers());
        } else if (insideLastFrame) {
            // Genau einmal beim Verlassen, nicht bei jeder Bewegung außerhalb.
            session.mouseLeft(view.toBrowserX(mouseX), view.toBrowserY(mouseY));
        }
        insideLastFrame = inside;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Wer klickt, arbeitet weiter und meint mit dem nächsten Escape nicht
        // das Schließen des Bildschirms.
        escapePending = false;
        if (session == null || view == null || !view.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // Ein Klick in die Fläche holt den Fokus zurück — sonst müsste man ihn
        // nach dem Zurückgeben mit einer Taste wiederholen.
        setFocus(BrowserFocus.BROWSER);
        session.mousePressed(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                button, heldModifiers(), System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (session == null || view == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        session.mouseReleased(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                button, heldModifiers());
        return true;
    }

    /**
     * Ziehen ist eine Bewegung mit gedrückter Taste.
     *
     * <p>Minecraft meldet es als eigene Art von Ereignis, Chromium kennt nur
     * Bewegungen — welche Tasten dabei unten sind, steht in den Flaggen. Die
     * Sitzung führt sie mit, deshalb genügt hier eine gewöhnliche Bewegung.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (session == null || view == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        session.mouseMoved(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                    heldModifiers());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (session == null || view == null || !view.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        session.mouseScrolled(view.toBrowserX(mouseX), view.toBrowserY(mouseY),
                scrollY, heldModifiers());
        return true;
    }

    // ---- Messung -----------------------------------------------------------

    /**
     * Schreibt den Messabschnitt ins Protokoll und beginnt einen neuen.
     *
     * <p>Die Frage dahinter ist nicht, ob der Bildweg schnell ist — das ist
     * gemessen. Die Frage ist, ob gewöhnliches Bedienen unerwartet große
     * Bereiche schmutzig macht. Ein Scrollen, das jedes Mal die ganze Seite
     * neu malt, kostet so viel wie eine Animation, und man sähe es nicht.
     */
    private void reportAndResetMeasurement() {
        reportSection(null);
    }

    /**
     * Wie {@link #reportAndResetMeasurement()}, aber mit Namen.
     *
     * <p>Für einen Ablauf, der die Abschnitte selbst kennt: „Rollen" sagt im
     * Protokoll mehr als „Abschnitt 3".
     */
    protected void reportSection(String label) {
        if (session == null) {
            return;
        }
        String name = label == null ? "Abschnitt " + measureSection : label;
        if (measuringSinceNanos != 0) {
            double seconds = (System.nanoTime() - measuringSinceNanos) / 1_000_000_000.0;
            long paints = session.paints() - paintsAtMeasureStart;
            long popupPaints = session.popupPaints() - popupPaintsAtMeasureStart;
            var main = session.texture();
            var popup = session.popupTexture();
            double fullFrame = (double) session.width() * session.height() * 4;
            LOG.info(String.format(java.util.Locale.GERMANY,
                    "%s über %.1f s: %d Bilder (%.1f/s), %d Uploads, "
                            + "davon %d Vollbild, %d Ausschnitte",
                    name, seconds, paints, paints / seconds,
                    main.uploads(), main.fullUploads(), main.regions()));
            LOG.info(String.format(java.util.Locale.GERMANY,
                    "%s: %.1f KB je Bild (%.2f %% eines Vollbilds), "
                            + "%.2f MB/s, Uploadzeit %s",
                    name, main.bytesPerUpload() / 1024.0,
                    main.bytesPerUpload() / fullFrame * 100.0,
                    main.uploadedBytes() / seconds / (1024.0 * 1024.0),
                    main.uploadTimes().summary()));
            if (popupPaints > 0) {
                LOG.info(String.format(java.util.Locale.GERMANY,
                        "%s: Popup %d Bilder, %d Uploads, %.1f KB gesamt",
                        name, popupPaints, popup.uploads(),
                        popup.uploadedBytes() / 1024.0));
            }
        }
        measureSection++;
        measuringSinceNanos = System.nanoTime();
        paintsAtMeasureStart = session.paints();
        popupPaintsAtMeasureStart = session.popupPaints();
        session.texture().resetStats();
        session.popupTexture().resetStats();
    }

    /** Ob überhaupt ein Browser läuft. */
    protected boolean hasSession() {
        return session != null;
    }

    /** Der laufende Browser, für alles, was mehr braucht als die Messwerte. */
    protected dev.devpanda.bamboocef.web.runtime.BrowserSession session() {
        return session;
    }

    /** Wie viele Bilder der Browser bisher geliefert hat. */
    protected long paints() {
        return session == null ? 0L : session.paints();
    }

    /** Wie lange es von einer Eingabe bis zum Bild dauert. */
    protected dev.devpanda.bamboocef.web.measure.DurationSamples inputLatency() {
        return session == null
                ? new dev.devpanda.bamboocef.web.measure.DurationSamples()
                : session.inputLatency();
    }

    /** Die Messwerte des Bildwegs zurück nach Minecraft. */
    protected dev.devpanda.bamboocef.web.texture.GlTextureBackend texture() {
        return session == null ? null : session.texture();
    }

    /**
     * Die gerade gedrückten Umschalttasten, als GLFW-Flaggen.
     *
     * <p><b>Ohne sie ist die halbe Maus tot.</b> Steuerung und Mausrad zoomen
     * einen Editor, Umschalt und Klick erweitern eine Auswahl, Alt und Klick
     * setzt einen zweiten Schreibcursor. Wer die Flaggen bei Mausereignissen
     * auf null lässt, nimmt der Seite all das — und es sieht aus, als könne
     * sie es nicht.
     *
     * <p>Bei Tastenereignissen liefert Minecraft die Flaggen mit; bei
     * Mausereignissen nicht, dort sind sie zu erfragen.
     */
    protected static int heldModifiers() {
        int modifiers = 0;
        if (hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }

    /**
     * Eine Taste samt Steuerung, wie sie von einer Tastatur käme.
     *
     * <p><b>Der Scancode ist auf Windows nicht optional.</b> Der native Teil
     * baut den Windows-Tastencode mit {@code MapVirtualKey} <b>aus dem
     * Scancode</b>; eine Null darin ergibt einen Tastencode von null, und
     * Chromium sieht eine Taste, die es nicht gibt. Bei echter Eingabe liefert
     * Minecraft ihn mit — wer Tasten selbst erzeugt, muss ihn erfragen.
     */
    protected void sendKeyCombination(int glfwKey, int modifiers) {
        int scanCode = GLFW.glfwGetKeyScancode(glfwKey);
        keyPressed(glfwKey, scanCode, modifiers);
        keyReleased(glfwKey, scanCode, modifiers);
    }

    /** Ob die Seite noch lädt — vorher verpufft jeder Aufruf in sie hinein. */
    protected boolean pageLoading() {
        return session == null || session.loading();
    }

    /** Reicht eine Zeile JavaScript an die Seite durch. */
    protected void runScript(String code) {
        if (session != null) {
            session.runScript(code);
        }
    }

    /**
     * Verkleinert die Fläche auf einen Anteil der vollen Größe.
     *
     * <p>Für die Messung: Eine Größenänderung lässt Chromium die ganze Seite
     * neu rechnen, und was das an Übertragung kostet, will gemessen sein.
     */
    protected void resizeTo(double factor) {
        if (session == null) {
            return;
        }
        view = new BrowserView(0, 0,
                Math.max(1, (int) (width * factor)),
                Math.max(1, (int) (height * factor)),
                minecraft.getWindow().getGuiScale());
        session.resize(view.browserWidth(), view.browserHeight());
    }

    // ---- Ende --------------------------------------------------------------

    /**
     * Aufräumen — und zwar hier.
     *
     * <p>{@code onClose()} kommt nur, wenn dieser Bildschirm sich selbst
     * schließt. Wer stattdessen einen anderen öffnet, lässt ihn hier zurück.
     * Ein Browser, der das überlebt, malt weiter und wird nie wieder gesehen.
     */
    @Override
    public void removed() {
        cursor.close(minecraft.getWindow().getWindow());
        if (session != null) {
            session.setFocused(false);
            session.close();
            session = null;
        }
        super.removed();
    }
}
