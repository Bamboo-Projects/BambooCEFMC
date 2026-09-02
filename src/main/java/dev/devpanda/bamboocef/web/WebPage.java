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
 * A single page from the jar onto disk, so that Chromium can load it.
 *
 * <p><b>Why onto disk.</b> Chromium does not read class paths. A page that sits
 * in the jar reaches it only as a file — under
 * {@code <game>/bamboocef/web/}, copied afresh on every call, so that a
 * changed page in the jar does not fail against an old copy.
 */
public final class WebPage {

    private static final Logger LOG = LogUtils.getLogger();

    private WebPage() {
    }

    /**
     * Writes the page out and returns its address.
     *
     * @param resource the path in the class path, for example
     *                 {@code assets/bamboocef/web/overlay/menu.html}
     * @param fileName the file name on disk
     * @return the {@code file:} address, or {@code null} if something was missing
     */
    public static String unpack(String resource, String fileName) {
        Path target = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("bamboocef").resolve("web").resolve(fileName);
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
