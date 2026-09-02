package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Eine einzelne Seite aus dem Jar auf die Platte, damit Chromium sie laden kann.
 *
 * <p><b>Warum auf die Platte.</b> Chromium liest keine Klassenpfade. Eine
 * Seite, die im Jar liegt, bekommt sie nur als Datei — unter
 * {@code <spiel>/factorynetwork/web/}, bei jedem Aufruf neu kopiert, damit
 * eine geänderte Seite im Jar nicht an einer alten Kopie scheitert.
 */
public final class WebPage {

    private static final Logger LOG = LogUtils.getLogger();

    private WebPage() {
    }

    /**
     * Legt die Seite ab und gibt ihre Adresse zurück.
     *
     * @param resource der Pfad im Klassenpfad, etwa
     *                 {@code assets/factorynetwork/web/overlay/menu.html}
     * @param fileName der Dateiname auf der Platte
     * @return die {@code file:}-Adresse, oder {@code null}, wenn etwas fehlte
     */
    public static String unpack(String resource, String fileName) {
        Path target = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("factorynetwork").resolve("web").resolve(fileName);
        try (InputStream stream = WebPage.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                LOG.warn("Die Seite fehlt im Klassenpfad: {}", resource);
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException broken) {
            LOG.warn("Die Seite {} ließ sich nicht ablegen", fileName, broken);
            return null;
        }
        return target.toUri().toString();
    }
}
