package dev.devpanda.bamboocef.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Was ausgeliefert wird: Fassung, Archivname, Prüfsumme, Adresse.
 *
 * <p>Gelesen aus {@code fn-runtime.properties} im Jar. Erzeugt wird die Datei
 * beim Paketieren und liegt eingecheckt unter
 * {@code tools/runtime/dist.properties} — die Mod muss wissen, was sie
 * erwartet, bevor irgendwer gepackt hat.
 *
 * <p><b>Je Plattform ein eigener Schlüssel.</b> Fehlt der für die laufende
 * Plattform, ist das kein Fehler, sondern eine Auskunft: Dafür gibt es keine
 * Binärdateien. Deshalb {@link #forThisPlatform()} und nicht ein Feld je
 * Angabe.
 */
public final class RuntimeManifest {

    /** Der Ort im Jar. Kommt aus tools/runtime/dist.properties. */
    private static final String RESOURCE = "/fn-runtime.properties";

    private static Properties values;

    /**
     * Nur für Prüfläufe: ein anderes Manifest unterschieben.
     *
     * <p>Seit im echten Manifest eine Adresse steht, würde ein Prüflauf über
     * {@link RuntimeInstall#locate} sonst wirklich laden — hundertsiebzig
     * Megabyte, bei jedem {@code gradle test}. {@code null} stellt das echte
     * wieder her.
     */
    static synchronized void useForTests(Properties override) {
        values = override;
    }

    private RuntimeManifest() {
    }

    private static synchronized Properties values() {
        if (values == null) {
            Properties read = new Properties();
            try (InputStream in = RuntimeManifest.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    read.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException broken) {
                // Eine unlesbare Datei ist wie keine: Der Aufrufer bekommt
                // dann UNSUPPORTED und einen Satz, der das erklärt.
            }
            values = read;
        }
        return values;
    }

    /** Die Fassung, die zu dieser Mod gehört, etwa {@code 146.0.10}. */
    public static String version() {
        return values().getProperty("runtime.version", "unbekannt");
    }

    /**
     * Woher heruntergeladen wird, ohne Schrägstrich am Ende — oder leer.
     *
     * <p><b>Leer ist ein gültiger Zustand</b>, kein Versehen: Dann sucht der
     * Client nur lokal und lädt nichts nach. So läuft die Entwicklung, solange
     * keine öffentliche Adresse steht.
     */
    public static String baseUrl() {
        String url = values().getProperty("runtime.base-url", "").trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Die Kennung der laufenden Plattform, etwa {@code windows-x86_64}. */
    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        String name = os.contains("win") ? "windows"
                : os.contains("mac") ? "macos"
                : "linux";
        // aarch64 und amd64 sind die Namen der JVM; im Archivnamen stehen die
        // von CEF.
        String bits = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
        return name + "-" + bits;
    }

    /** Was für diese Plattform ausgeliefert wird, oder {@code null}. */
    public static Entry forThisPlatform() {
        String platform = platform();
        String archive = values().getProperty("runtime." + platform + ".archive");
        String sha256 = values().getProperty("runtime." + platform + ".sha256");
        if (archive == null || archive.isBlank() || sha256 == null || sha256.isBlank()) {
            return null;
        }
        return new Entry(platform, archive, sha256.toLowerCase(java.util.Locale.ROOT));
    }

    /** Archiv und Prüfsumme einer Plattform. */
    public record Entry(String platform, String archive, String sha256) {

        /** Die volle Adresse, oder {@code null}, wenn keine Basis gesetzt ist. */
        public String url() {
            String base = baseUrl();
            return base.isEmpty() ? null : base + "/" + archive;
        }
    }
}
