package dev.devpanda.bamboocef.web;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Where the runtime sits — and how it gets there.
 *
 * <p><b>Three places, in this order.</b>
 *
 * <pre>
 *   1. bamboocef.runtime.dir     set by the build script, for development
 *   2. &lt;game directory&gt;/bamboocef/runtime/&lt;version&gt;/
 *   3. downloaded, if an address is given in the manifest
 * </pre>
 *
 * <p><b>The version is in the path</b>, and that is not tidiness: without it,
 * after a bump of the CEF version the old state would silently keep starting,
 * and the new mod would run against old libraries.
 *
 * <p><b>None of this loads on the render thread.</b> The caller stands there and
 * gets an answer at once — including the answer "currently loading". A first
 * screen change must not hang for a hundred and seventy megabytes.
 */
public final class RuntimeInstall {

    private static final Logger LOG = LogUtils.getLogger();

    /** How a complete folder can be recognized. */
    private static final String MARKER = "jcef.jar";

    /**
     * How an <b>in</b>complete one can be recognized.
     *
     * <p>This file is created before unpacking and disappears afterwards.
     * Without it, two hundred half-unpacked files would look like a finished
     * runtime, and the error would come later as an
     * {@code UnsatisfiedLinkError} deep out of {@code CefApp} instead of here as
     * information.
     */
    private static final String INCOMPLETE = ".unvollstaendig";

    /** After how many bytes the next progress line is due. */
    private static final long REPORT_EVERY = 20L * 1024 * 1024;

    private static volatile boolean downloading;

    private RuntimeInstall() {
    }

    /**
     * The folder with the native libraries.
     *
     * @throws WebRuntimeUnavailable if it is missing — with the state that says
     *         why, and whether a second attempt is worthwhile
     */
    public static File locate(File gameDirectory) {
        String configured = System.getProperty("bamboocef.runtime.dir");
        if (configured != null && !configured.isBlank()) {
            File dir = new File(configured);
            if (complete(dir)) {
                return dir;
            }
            throw new WebRuntimeUnavailable(WebRuntimeState.RUNTIME_MISSING,
                    "Keine Laufzeitumgebung unter " + dir
                            + " — erst: pwsh -File tools/runtime/build-jcef.ps1");
        }

        File cached = cacheDir(gameDirectory);
        if (complete(cached)) {
            return cached;
        }

        RuntimeManifest.Entry entry = RuntimeManifest.forThisPlatform();
        if (entry == null) {
            throw new WebRuntimeUnavailable(WebRuntimeState.UNSUPPORTED,
                    "Für " + RuntimeManifest.platform() + " gibt es keine Binärdateien");
        }
        String url = entry.url();
        if (url == null) {
            throw new WebRuntimeUnavailable(WebRuntimeState.RUNTIME_MISSING,
                    "Die Laufzeitumgebung liegt nicht unter " + cached
                            + ", und es ist keine Adresse zum Nachladen hinterlegt");
        }

        startDownload(entry, url, cached);
        throw new WebRuntimeUnavailable(WebRuntimeState.NOT_DOWNLOADED,
                "Die Laufzeitumgebung wird geladen (" + entry.archive() + ")");
    }

    /** Where a downloaded runtime sits. */
    public static File cacheDir(File gameDirectory) {
        File own = new File(gameDirectory, "bamboocef");
        return new File(new File(own, "runtime"), RuntimeManifest.version());
    }

    /** Is the folder there and fully unpacked? */
    public static boolean complete(File dir) {
        return new File(dir, MARKER).isFile() && !new File(dir, INCOMPLETE).exists();
    }

    /** Is a download running right now? */
    public static boolean downloading() {
        return downloading;
    }

    // ---- Downloading --------------------------------------------------------

    private static synchronized void startDownload(RuntimeManifest.Entry entry,
                                                   String url, File target) {
        if (downloading) {
            return;
        }
        downloading = true;
        Thread worker = new Thread(() -> {
            try {
                download(entry, url, target.toPath());
                LOG.info("Laufzeitumgebung liegt jetzt unter {}", target);
            } catch (Throwable broken) {
                LOG.error("Die Laufzeitumgebung ließ sich nicht laden", broken);
            } finally {
                downloading = false;
            }
        }, "BambooCEF Laufzeitumgebung");
        // A background thread: whoever quits the game while it is downloading
        // should not wait for a hundred and seventy megabytes. What is
        // half-loaded sits in a .teil file and does not get in the way next
        // time.
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Download, verify, unpack — in this order and no other.
     *
     * <p>Work on the target folder only begins once the checksum matches, and
     * then under a marker. Between the start and the end of unpacking there is
     * thus no moment in which the folder looks finished.
     *
     * <p>Package-private and not private, so that a test run can call it against
     * its own server. On the path through {@link #locate(File)} it is never
     * reached in a development run — there {@code bamboocef.runtime.dir} is set, and
     * then everything is already present.
     */
    static void download(RuntimeManifest.Entry entry, String url, Path target)
            throws IOException {
        Path partial = target.resolveSibling(entry.archive() + ".teil");
        Files.createDirectories(partial.getParent());
        LOG.info("Lade die Laufzeitumgebung: {}", url);

        String actual = fetch(url, partial);
        if (!entry.sha256().equals(actual)) {
            Files.deleteIfExists(partial);
            throw new IOException("Die Prüfsumme stimmt nicht: erwartet " + entry.sha256()
                    + ", bekommen " + actual);
        }

        Files.createDirectories(target);
        Path marker = target.resolve(INCOMPLETE);
        Files.writeString(marker, url);
        unpack(partial, target);
        Files.deleteIfExists(marker);
        Files.deleteIfExists(partial);
    }

    /** Downloads and returns the checksum of what was downloaded. */
    private static String fetch(String url, Path into) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Die Adresse antwortet mit " + code + ": " + url);
            }
            long expected = connection.getContentLengthLong();
            MessageDigest digest = sha256();
            try (InputStream in = new DigestInputStream(connection.getInputStream(), digest);
                 OutputStream out = Files.newOutputStream(into)) {
                copy(in, out, expected);
            }
            return HexFormat.of().formatHex(digest.digest());
        } finally {
            connection.disconnect();
        }
    }

    private static void copy(InputStream in, OutputStream out, long expected) throws IOException {
        byte[] buffer = new byte[65536];
        long done = 0;
        long reported = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            done += read;
            // One line every twenty megabytes: enough to see that it is making
            // progress, little enough not to bury the log.
            if (done - reported >= REPORT_EVERY) {
                reported = done;
                LOG.info("Laufzeitumgebung: {} von {} MB", done / REPORT_EVERY * 20,
                        expected > 0 ? String.valueOf(expected / 1024 / 1024) : "?");
            }
        }
    }

    /**
     * Unpacks the archive.
     *
     * <p><b>Via {@code tar} and not via a library.</b> Windows has shipped it
     * since Ten, Linux and macOS anyway — and a tar.gz library would be a
     * dependency for twenty lines that the operating system already provides.
     */
    private static void unpack(Path archive, Path into) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "tar", "-xzf", archive.toAbsolutePath().toString(),
                "-C", into.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("tar scheiterte: " + output.trim());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Das Auspacken wurde unterbrochen", interrupted);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandatory in every JVM.
            throw new IllegalStateException(impossible);
        }
    }
}
