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
 * The development environment as a screen.
 *
 * <p>It inherits everything so far: the image path from Steps A to C, input and
 * focus from D, Minecraft's image as the background from E. New is only what
 * runs on it — and this time that is not a test pattern but an editor.
 *
 * <p><b>The background is fixed to still image, half edge length, raw
 * format.</b> That is the result of Step E, and here it is not a
 * setting: what should be measured is Monaco, not the already known
 * worst case of the background.
 *
 * <p><b>Why a file address and not a scheme of our own.</b> Monaco's
 * loader fetches dozens of files via relative addresses. With a
 * self-registered scheme the page has no origin, and relative
 * addresses do not resolve. A file address turns this into ordinary
 * web — with one caveat that comes with it: worker threads are refused by
 * Chromium from there. Monaco copes with that because our language gets its
 * highlighting on the main thread.
 */
public class IdeScreen extends BackdropScreen {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/IDE");

    /** Where the web interface's file manifest lives in the classpath. */
    private static final String MANIFEST = "assets/factorynetwork/web/ide/files.txt";

    /** And where the page itself. */
    private static final String PAGE = "assets/factorynetwork/web/ide/index.html";

    /** The folder under the game folder into which things are unpacked. */
    private static final String UNPACKED = "factorynetwork-web";

    protected IdeScreen(String url) {
        super(Component.literal("Factory Network IDE"), url,
                Mode.STATIC, 0.5, WorldCapture.Format.BMP);
    }

    /**
     * Unpacks if needed and opens the interface.
     *
     * @return whether it worked
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
     * Unpacks and returns the address of the page.
     *
     * <p>Separated from opening so that the benchmark uses the same preparation
     * and does not keep its own alongside.
     *
     * @return the address, or {@code null} if something was missing
     */
    protected static String prepare(Minecraft client) {
        Path into = client.gameDirectory.toPath().resolve(UNPACKED).resolve("ide");
        ClassLoader loader = IdeScreen.class.getClassLoader();
        Path folder = WebAssets.unpack(loader, MANIFEST, into);
        if (folder == null) {
            return null;
        }
        // The page itself lies next to the manifest and is not listed in it —
        // it is our work, not part of the bundle.
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
