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
 * Packt die Weboberfläche aus der Mod in einen Ordner, den ein Browser lesen
 * kann.
 *
 * <p><b>Warum überhaupt auspacken.</b> Die Dateien liegen im Klassenpfad — in
 * der Entwicklung als Ordner, ausgeliefert in einem Jar. Chromium kann weder
 * das eine noch das andere lesen; es braucht eine Adresse. Ein eigenes Schema
 * wäre eine, aber dann müsste jede relative Adresse innerhalb der Seite durch
 * unseren Handler, und ein Ladeprogramm wie das von Monaco erzeugt Dutzende
 * davon. Eine Datei-Adresse macht daraus wieder gewöhnliches Web.
 *
 * <p><b>Und warum das kein Herunterladen ist.</b> Alles, was hier bewegt wird,
 * kommt aus der Mod selbst. Eine Oberfläche, die beim ersten Öffnen ins Netz
 * greift, funktioniert im Zug nicht und in einem gesperrten Netz nie.
 *
 * <p><b>Ein Verzeichnis statt einer Suche.</b> Ein Ordner im Klassenpfad lässt
 * sich nicht zuverlässig auflisten — im Jar gibt es keine Ordner, nur Einträge
 * mit Schrägstrichen im Namen. Deshalb legt das Bündelskript eine Datei mit
 * allen Namen daneben, und hier wird sie gelesen.
 *
 * <p>Ausgepackt wird einmal je Fassung. Die erste Zeile des Verzeichnisses
 * trägt die Fassung; steht dieselbe schon im Zielordner, bleibt alles liegen.
 */
public final class WebAssets {

    private static final Logger LOG = LoggerFactory.getLogger("FactoryNetwork/WebAssets");

    /** Woran der Zielordner erkennt, was in ihm liegt. */
    private static final String STAMP = ".fassung";

    private WebAssets() {
    }

    /**
     * Packt aus, falls nötig, und gibt den Zielordner zurück.
     *
     * @param loader       woher die Dateien kommen
     * @param manifestPath der Pfad des Verzeichnisses im Klassenpfad
     * @param into         wohin — ein Ordner, der angelegt werden darf
     * @return der Ordner, oder {@code null}, wenn etwas fehlte
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

    /** Ob dieselbe Fassung schon dort liegt. */
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
