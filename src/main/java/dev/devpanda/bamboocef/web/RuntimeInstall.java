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
 * Wo die Laufzeitumgebung liegt — und wie sie dorthin kommt.
 *
 * <p><b>Drei Orte, in dieser Reihenfolge.</b>
 *
 * <pre>
 *   1. fn.runtime.dir            gesetzt vom Buildskript, für Entwicklung
 *   2. &lt;Spielordner&gt;/factorynetwork/runtime/&lt;Fassung&gt;/
 *   3. heruntergeladen, wenn eine Adresse im Manifest steht
 * </pre>
 *
 * <p><b>Die Fassung steht im Pfad</b>, und das ist kein Ordnungssinn: Ohne sie
 * startete nach einem Anheben der CEF-Fassung kommentarlos der alte Stand
 * weiter, und die neue Mod liefe gegen alte Bibliotheken.
 *
 * <p><b>Nichts hiervon lädt im Renderthread.</b> Der Aufrufer steht dort und
 * bekommt sofort eine Antwort — auch die Antwort „wird gerade geladen". Ein
 * erster Bildschirmwechsel darf nicht hundertsiebzig Megabyte lang hängen.
 */
public final class RuntimeInstall {

    private static final Logger LOG = LogUtils.getLogger();

    /** Woran ein vollständiger Ordner zu erkennen ist. */
    private static final String MARKER = "jcef.jar";

    /**
     * Woran ein <b>un</b>vollständiger zu erkennen ist.
     *
     * <p>Diese Datei entsteht vor dem Auspacken und verschwindet danach. Ohne
     * sie sähen zweihundert halb ausgepackte Dateien wie eine fertige
     * Laufzeitumgebung aus, und der Fehler käme später als
     * {@code UnsatisfiedLinkError} tief aus {@code CefApp} statt hier als
     * Auskunft.
     */
    private static final String INCOMPLETE = ".unvollstaendig";

    /** Ab wie vielen Bytes die nächste Fortschrittszeile fällig ist. */
    private static final long REPORT_EVERY = 20L * 1024 * 1024;

    private static volatile boolean downloading;

    private RuntimeInstall() {
    }

    /**
     * Der Ordner mit den nativen Bibliotheken.
     *
     * @throws WebRuntimeUnavailable wenn er fehlt — mit dem Zustand, der sagt,
     *         warum, und ob ein zweiter Versuch lohnt
     */
    public static File locate(File gameDirectory) {
        String configured = System.getProperty("fn.runtime.dir");
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

    /** Wo eine nachgeladene Laufzeitumgebung liegt. */
    public static File cacheDir(File gameDirectory) {
        File own = new File(gameDirectory, "factorynetwork");
        return new File(new File(own, "runtime"), RuntimeManifest.version());
    }

    /** Ist der Ordner da und vollständig ausgepackt? */
    public static boolean complete(File dir) {
        return new File(dir, MARKER).isFile() && !new File(dir, INCOMPLETE).exists();
    }

    /** Läuft gerade ein Download? */
    public static boolean downloading() {
        return downloading;
    }

    // ---- Herunterladen ------------------------------------------------------

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
        }, "FactoryNetwork Laufzeitumgebung");
        // Ein Hintergrundthread: Wer das Spiel beendet, während geladen wird,
        // soll nicht auf hundertsiebzig Megabyte warten. Was halb geladen ist,
        // liegt in einer .teil-Datei und stört beim nächsten Mal nicht.
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Laden, prüfen, auspacken — in dieser Reihenfolge und nicht anders.
     *
     * <p>Am Zielordner wird erst gearbeitet, wenn die Prüfsumme stimmt, und
     * dann unter einem Marker. Zwischen Anfang und Ende des Auspackens gibt es
     * damit keinen Augenblick, in dem der Ordner fertig aussieht.
     *
     * <p>Paketprivat und nicht privat, damit ein Prüflauf sie gegen einen
     * eigenen Server aufrufen kann. Auf dem Weg über {@link #locate(File)}
     * wird sie im Entwicklungslauf nie erreicht — dort steht
     * {@code fn.runtime.dir}, und dann ist alles schon da.
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

    /** Lädt herunter und gibt die Prüfsumme des Geladenen zurück. */
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
            // Alle zwanzig Megabyte eine Zeile: genug, um zu sehen, dass es
            // vorangeht, wenig genug, um das Protokoll nicht zuzuschütten.
            if (done - reported >= REPORT_EVERY) {
                reported = done;
                LOG.info("Laufzeitumgebung: {} von {} MB", done / REPORT_EVERY * 20,
                        expected > 0 ? String.valueOf(expected / 1024 / 1024) : "?");
            }
        }
    }

    /**
     * Packt das Archiv aus.
     *
     * <p><b>Über {@code tar} und nicht über eine Bibliothek.</b> Windows
     * liefert es seit Zehn mit, Linux und macOS ohnehin — und eine
     * tar.gz-Bibliothek wäre eine Abhängigkeit für zwanzig Zeilen, die es im
     * Betriebssystem schon gibt.
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
            // SHA-256 ist in jeder JVM Pflicht.
            throw new IllegalStateException(impossible);
        }
    }
}
