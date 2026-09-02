package dev.devpanda.bamboocef.web.ide;

import dev.devpanda.bamboocef.web.WebAssets;
import dev.devpanda.bamboocef.web.capture.WorldCapture;
import dev.devpanda.bamboocef.web.screen.BackdropScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Die Entwicklungsumgebung als Bildschirm.
 *
 * <p>Sie erbt alles Bisherige: den Bildweg aus Schritt A bis C, Eingabe und
 * Fokus aus D, Minecrafts Bild als Hintergrund aus E. Neu ist nur, was darauf
 * läuft — und das ist diesmal kein Prüfbild, sondern ein Editor.
 *
 * <p><b>Der Hintergrund steht fest auf Standbild, halbe Kantenlänge, rohes
 * Format.</b> Das ist das Ergebnis von Schritt E, und es ist hier keine
 * Einstellung: Gemessen werden soll Monaco, nicht der bereits bekannte
 * schlimmste Fall des Hintergrunds.
 *
 * <p><b>Warum eine Datei-Adresse und kein eigenes Schema.</b> Monacos
 * Ladeprogramm holt Dutzende Dateien mit relativen Adressen nach. Bei einem
 * selbst angemeldeten Schema hat die Seite keine Herkunft, und relative
 * Adressen lösen sich nicht auf. Eine Datei-Adresse macht daraus gewöhnliches
 * Web — mit einer Einschränkung, die dazugehört: Arbeitsfäden verweigert
 * Chromium von dort. Monaco kommt damit zurecht, weil unsere Sprache ihre
 * Einfärbung im Hauptfaden bekommt.
 */
public class IdeScreen extends BackdropScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/IDE");

    /** Wo das Verzeichnis der Weboberfläche im Klassenpfad liegt. */
    private static final String MANIFEST = "assets/factorynetwork/web/ide/files.txt";

    /** Und wo die Seite selbst. */
    private static final String PAGE = "assets/factorynetwork/web/ide/index.html";

    /** Der Ordner unter dem Spielordner, in den ausgepackt wird. */
    private static final String UNPACKED = "factorynetwork-web";

    protected IdeScreen(String url) {
        super(Component.literal("Factory Network IDE"), url,
                Mode.STATIC, 0.5, WorldCapture.Format.BMP);
    }

    /**
     * Packt aus, falls nötig, und öffnet die Oberfläche.
     *
     * @return ob es geklappt hat
     */
    public static boolean open(Minecraft client) {
        String url = prepare(client);
        if (url == null) {
            return false;
        }
        client.setScreen(new IdeScreen(url));
        return true;
    }

    /**
     * Packt aus und gibt die Adresse der Seite zurück.
     *
     * <p>Getrennt vom Öffnen, damit die Messung dieselbe Vorbereitung nutzt
     * und nicht ihre eigene daneben hat.
     *
     * @return die Adresse, oder {@code null}, wenn etwas fehlte
     */
    protected static String prepare(Minecraft client) {
        Path into = client.gameDirectory.toPath().resolve(UNPACKED).resolve("ide");
        ClassLoader loader = IdeScreen.class.getClassLoader();
        Path folder = WebAssets.unpack(loader, MANIFEST, into);
        if (folder == null) {
            return null;
        }
        // Die Seite selbst liegt neben dem Verzeichnis und steht nicht darin —
        // sie ist unser Werk, nicht Teil des Bündels.
        Path page = folder.resolve("index.html");
        try {
            java.nio.file.Files.createDirectories(folder);
            try (java.io.InputStream stream = loader.getResourceAsStream(PAGE)) {
                if (stream == null) {
                    LOG.warn("Die Seite fehlt im Klassenpfad: {}", PAGE);
                    return null;
                }
                java.nio.file.Files.copy(stream, page,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.io.IOException broken) {
            LOG.warn("Die Seite ließ sich nicht ablegen", broken);
            return null;
        }
        return page.toUri().toString();
    }
}
