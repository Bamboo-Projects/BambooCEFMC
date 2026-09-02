package dev.devpanda.bamboocef.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Unpacks the web interface from the mod into a folder that a browser can
 * read.
 *
 * <p><b>Why unpack at all.</b> The files sit in the class path — in development
 * as a folder, shipped in a jar. Chromium can read neither one nor the other;
 * it needs an address. A custom scheme would be one, but then every relative
 * address inside the page would have to go through our handler, and a loader
 * like Monaco's produces dozens of them. A file address turns that back into
 * ordinary web.
 *
 * <p><b>And why this is not a download.</b> Everything moved here comes from
 * the mod itself. An interface that reaches out to the network on first open
 * does not work on a train and never in a locked-down network.
 *
 * <p><b>A directory instead of a search.</b> A folder in the class path cannot
 * be listed reliably — in the jar there are no folders, only entries with
 * slashes in the name. That is why the bundle script writes a file with all the
 * names next to it, and here it is read.
 *
 * <p>Unpacking happens once per version. The first line of the directory
 * carries the version; if the same one already sits in the target folder,
 * everything stays put.
 */
public final class WebAssets {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebAssets");

    /** How the target folder tells what sits in it. */
    private static final String STAMP = ".fassung";

    private WebAssets() {
    }

    /**
     * Unpacks if necessary and returns the target folder.
     *
     * @param loader       where the files come from
     * @param manifestPath the path of the directory in the class path
     * @param into         where to — a folder that may be created
     * @return the folder, or {@code null} if something was missing
     */
    public static Path unpack(ClassLoader loader, String manifestPath, Path into) {
        List<String> names;
        String version;
        try (InputStream stream = loader.getResourceAsStream(manifestPath)) {
            if (stream == null) {
                LOG.warn("Kein Verzeichnis unter {} — ist tools/monaco.py gelaufen?",
                        manifestPath);
                return null;
            }
            List<String> lines = readLines(stream);
            version = lines.isEmpty() ? "?" : lines.get(0);
            names = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    names.add(trimmed);
                }
            }
        } catch (IOException broken) {
            LOG.warn("Das Verzeichnis unter {} ließ sich nicht lesen", manifestPath, broken);
            return null;
        }

        if (alreadyThere(into, version)) {
            return into;
        }

        String base = manifestPath.substring(0, manifestPath.lastIndexOf('/') + 1);
        long started = System.nanoTime();
        long bytes = 0;
        try {
            Files.createDirectories(into);
            for (String name : names) {
                bytes += copy(loader, base + name, into.resolve(name));
            }
            Files.writeString(into.resolve(STAMP), version, StandardCharsets.UTF_8);
        } catch (IOException broken) {
            LOG.warn("Die Oberfläche ließ sich nicht auspacken", broken);
            return null;
        }
        LOG.info(String.format(java.util.Locale.GERMANY,
                "Oberfläche ausgepackt: %d Dateien, %.1f MB, %.0f ms — %s",
                names.size(), bytes / 1048576.0,
                (System.nanoTime() - started) / 1_000_000.0, version));
        return into;
    }

    /** Whether the same version already sits there. */
    private static boolean alreadyThere(Path into, String version) {
        Path stamp = into.resolve(STAMP);
        try {
            return Files.exists(stamp)
                    && Files.readString(stamp, StandardCharsets.UTF_8).equals(version);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static long copy(ClassLoader loader, String from, Path to) throws IOException {
        try (InputStream stream = loader.getResourceAsStream(from)) {
            if (stream == null) {
                throw new IOException("Fehlt im Klassenpfad: " + from);
            }
            Files.createDirectories(to.getParent());
            return Files.copy(stream, to,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
