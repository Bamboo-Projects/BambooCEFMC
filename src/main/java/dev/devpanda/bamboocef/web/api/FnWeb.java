package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.BrowserManager;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.WebSupport;
import dev.devpanda.bamboocef.web.runtime.BrowserSession;

/**
 * Der Einstieg in die Web-Runtime.
 *
 * <p>Mit dieser Klasse erzeugt Mod-Code Web-Flächen: für einen Bildschirm,
 * für ein Overlay über dem Bild, für eine Fläche in der Welt. Was darunter
 * liegt — Chromium, seine Sitzungen, seine Texturen — bleibt drinnen.
 *
 * <pre>
 * WebSurface fläche = FnWeb.open(
 *         SurfaceSpec.of("https://example.org", 512, 512)
 *                 .named("Schnellmenü")
 *                 .keys(KeyFilter.only(GLFW.GLFW_KEY_ESCAPE)));
 * if (fläche != null) {
 *     // zeichnen über fläche.textureLocation(), schließen mit close()
 * }
 * </pre>
 *
 * <p><b>Drei Zusagen.</b>
 *
 * <ol>
 *   <li><b>Kein Typ aus {@code org.cef} in einer Signatur.</b> Wer diese
 *       Schnittstelle benutzt, übersetzt nicht gegen Chromium — und die
 *       Fassung darunter darf wechseln, ohne fremden Code zu brechen.</li>
 *   <li><b>Nichts wirft.</b> Fehlt die Laufzeitumgebung, kommt {@code null}
 *       zurück und {@link #status()} sagt warum. Ein fehlender Browser kostet
 *       eine Oberfläche und nicht den Client.</li>
 *   <li><b>Alles gehört dem Renderthread.</b> Chromiums Sitzungen leben dort.
 *       Ein Aufruf von woanders ist kein Zufallsfehler.</li>
 * </ol>
 *
 * <p><b>Fassung 1.</b> Was hier öffentlich steht, bleibt es; neue Angaben
 * kommen als weitere Methode an {@link SurfaceSpec} und nicht als weiterer
 * Parameter.
 */
public final class FnWeb {

    private FnWeb() {
    }

    /**
     * Öffnet eine Fläche.
     *
     * <p>Fährt die Laufzeitumgebung hoch, falls sie noch steht. Beim ersten
     * Aufruf dauert das ein paar hundert Millisekunden — deshalb beim ersten
     * Bedarf zu rufen und nicht beim Start des Spiels.
     *
     * @return die Fläche, oder {@code null}, wenn es gerade keine geben kann.
     *         Der Grund steht in {@link #status()}.
     */
    public static WebSurface open(SurfaceSpec spec) {
        if (!available()) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    spec.width(), spec.height(), spec.visibility(), spec.name());
            return new SessionSurface(session, spec.keys());
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Die Fläche {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Kann gerade eine Fläche entstehen?
     *
     * <p>Fährt die Laufzeitumgebung dabei hoch, wenn sie noch nicht läuft.
     */
    /**
     * Öffnet eine Fläche über dem Bild.
     *
     * <p>Breite und Höhe des Bauplans gelten hier als <b>Bildschirmpunkte</b>,
     * nicht als Pixel — dieselbe Einheit wie {@code x} und {@code y}. Die
     * Fläche rechnet mit der GUI-Skalierung in Browserpixel um und zieht bei
     * einem Wechsel nach. Ohne Fokus ist sie ein Bild; siehe
     * {@link WebOverlay#focus(OverlayFocus)}.
     *
     * @param x linker Rand in Bildschirmpunkten
     * @param y oberer Rand in Bildschirmpunkten
     * @return das Overlay, oder {@code null}, wenn es keinen Browser gibt
     */
    public static WebOverlay openOverlay(SurfaceSpec spec, int x, int y) {
        if (!available()) {
            return null;
        }
        try {
            double guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            dev.devpanda.bamboocef.web.view.BrowserView view =
                    new dev.devpanda.bamboocef.web.view.BrowserView(
                            x, y, spec.width(), spec.height(), guiScale);
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    view.browserWidth(), view.browserHeight(), spec.visibility(), spec.name());
            OverlayImpl overlay = new OverlayImpl(new SessionSurface(session, spec.keys()), view);
            Overlays.add(overlay);
            return overlay;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Das Overlay {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Öffnet eine Fläche in der Welt.
     *
     * <p>Ort ist der Mittelpunkt in Weltkoordinaten, der Gierwinkel folgt
     * Minecrafts Konvention (siehe {@link WorldSurface}), Breite und Höhe sind
     * in Blöcken. Die Auflösung der Seite steht im {@code spec} in Pixeln und
     * ist davon unabhängig.
     *
     * @return die Fläche, oder {@code null}, wenn es keinen Browser gibt
     */
    public static WorldSurface openInWorld(SurfaceSpec spec, double x, double y, double z,
                                           float yaw, float widthBlocks, float heightBlocks) {
        if (!available()) {
            return null;
        }
        try {
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    spec.width(), spec.height(), spec.visibility(), spec.name());
            WorldSurfaceImpl surface = new WorldSurfaceImpl(
                    new SessionSurface(session, spec.keys()),
                    x, y, z, yaw, widthBlocks, heightBlocks);
            WorldSurfaces.add(surface);
            return surface;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Die Weltfläche {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    /**
     * Öffnet eine Fläche als Bildschirm über das ganze Bild.
     *
     * <p>Die Größe kommt vom Fenster, nicht aus dem Bauplan — ein Bildschirm
     * füllt es und zieht bei einer Skalierungsänderung nach. Der Grund
     * scheint durch, wenn der Bauplan {@code transparent} ist; dahinter liegt
     * Minecrafts unscharfe Welt.
     *
     * <p>Lässt der Bauplan die Tasten offen, bekommt der Bildschirm alle —
     * ein Editor ohne Tasten wäre sinnlos, und das Spiel ruht ohnehin,
     * solange ein Bildschirm offen ist.
     *
     * <p>Zweimal Escape schließt, F10 in jedem Fall. Der Aufrufer hängt seinen
     * {@code onMessage} an die zurückgegebene Fläche und prüft {@code alive()};
     * das Schließen besorgt der Bildschirm.
     *
     * @return die Fläche, oder {@code null}, wenn es keinen Browser gibt
     */
    public static WebSurface openScreen(SurfaceSpec spec) {
        if (!available()) {
            return null;
        }
        try {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            var window = client.getWindow();
            dev.devpanda.bamboocef.web.view.BrowserView view =
                    dev.devpanda.bamboocef.web.view.BrowserView.fullscreen(
                            window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                            window.getGuiScale());
            KeyFilter keys = spec.keys() == KeyFilter.NONE ? KeyFilter.ALL : spec.keys();
            BrowserSession session = BrowserSession.open(spec.url(), spec.transparent(),
                    view.browserWidth(), view.browserHeight(),
                    dev.devpanda.bamboocef.web.BrowserVisibility.FOREGROUND, spec.name());
            SessionSurface surface = new SessionSurface(session, keys);
            client.setScreen(new WebScreenImpl(surface, view, spec.transparent()));
            return surface;
        } catch (Throwable broken) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Der Web-Bildschirm {} kam nicht zustande", spec.name(), broken);
            return null;
        }
    }

    public static boolean available() {
        return WebSupport.ensureStarted().usable();
    }

    /**
     * Warum es geht oder nicht geht.
     *
     * <p>Fünf Zustände und kein Wahrheitswert: Wem die Laufzeitumgebung fehlt,
     * braucht einen anderen Satz als wer hinter einem Proxy sitzt.
     */
    public static WebRuntimeStatus status() {
        return dev.devpanda.bamboocef.web.WebRuntime.status();
    }

    /**
     * Wie viele Browser gerade leben — über alle Flächen hinweg.
     *
     * <p>Zum Nachsehen, nicht zum Steuern: Wer eine eigene Obergrenze braucht,
     * zählt seine eigenen Flächen. Diese Zahl enthält auch die von anderen.
     */
    public static int liveCount() {
        return BrowserManager.count();
    }
}
