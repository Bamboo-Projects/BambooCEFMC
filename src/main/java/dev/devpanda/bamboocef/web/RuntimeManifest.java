package dev.devpanda.bamboocef.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * What ships: version, archive name, checksum, address.
 *
 * <p>Read from {@code bamboocef-runtime.properties} in the jar. The file is generated
 * at packaging time and sits checked in under
 * {@code tools/runtime/dist.properties} — the mod has to know what it expects
 * before anyone has packaged anything.
 *
 * <p><b>One key per platform.</b> If the one for the running platform is
 * missing, that is not an error but information: there are no binaries for it.
 * Hence {@link #forThisPlatform()} and not one field per entry.
 */
public final class RuntimeManifest {

    /** The location in the jar. Comes from tools/runtime/dist.properties. */
    private static final String RESOURCE = "/bamboocef-runtime.properties";

    private static Properties values;

    /**
     * For test runs only: slip in a different manifest.
     *
     * <p>Now that the real manifest carries an address, a test run through
     * {@link RuntimeInstall#locate} would otherwise really download — a hundred
     * and seventy megabytes, on every {@code gradle test}. {@code null} restores
     * the real one.
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
                // An unreadable file is like none: the caller then gets
                // UNSUPPORTED and a sentence that explains it.
            }
            values = read;
        }
        return values;
    }

    /** The version that belongs to this mod, for example {@code 146.0.10}. */
    public static String version() {
        return values().getProperty("runtime.version", "unbekannt");
    }

    /**
     * Where it is downloaded from, without a trailing slash — or empty.
     *
     * <p><b>Empty is a valid state</b>, not an oversight: then the client only
     * looks locally and downloads nothing. That is how development runs, as long
     * as no public address is set.
     */
    public static String baseUrl() {
        String url = values().getProperty("runtime.base-url", "").trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The identifier of the running platform, for example {@code windows-x86_64}. */
    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        String name = os.contains("win") ? "windows"
                : os.contains("mac") ? "macos"
                : "linux";
        // aarch64 and amd64 are the JVM's names; the archive name uses CEF's.
        String bits = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
        return name + "-" + bits;
    }

    /** What ships for this platform, or {@code null}. */
    public static Entry forThisPlatform() {
        String platform = platform();
        String archive = values().getProperty("runtime." + platform + ".archive");
        String sha256 = values().getProperty("runtime." + platform + ".sha256");
        if (archive == null || archive.isBlank() || sha256 == null || sha256.isBlank()) {
            return null;
        }
        return new Entry(platform, archive, sha256.toLowerCase(java.util.Locale.ROOT));
    }

    /** Archive and checksum of a platform. */
    public record Entry(String platform, String archive, String sha256) {

        /** The full address, or {@code null} if no base is set. */
        public String url() {
            String base = baseUrl();
            return base.isEmpty() ? null : base + "/" + archive;
        }
    }
}
