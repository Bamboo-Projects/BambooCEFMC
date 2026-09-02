package dev.devpanda.bamboocef.web;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wo die Laufzeitumgebung gesucht und wie sie geholt wird.
 *
 * <p><b>Warum diese Prüfläufe wichtiger sind als die meisten.</b> Im
 * Entwicklungslauf ist {@code fn.runtime.dir} gesetzt, und damit endet die
 * Suche in der ersten Zeile. Alles dahinter — Zwischenlager, Download,
 * Prüfsumme, halb ausgepackter Ordner — wird beim Entwickeln <b>nie</b>
 * berührt und beim Spieler <b>immer</b>.
 *
 * <p>Der Server ist einer aus der Java-Bibliothek und läuft auf einem Port,
 * den das Betriebssystem aussucht. Kein Netz, keine fremde Adresse, keine
 * Wartezeit.
 */
class RuntimeInstallTest {

    private HttpServer server;

    @AfterEach
    void cleanUp() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        System.clearProperty("fn.runtime.dir");
        RuntimeManifest.useForTests(null);
    }

    /**
     * Ein Manifest für den Prüflauf.
     *
     * <p>Das echte im Klassenpfad nennt seit der Auslieferung eine Adresse —
     * wer damit {@code locate} ruft, lädt wirklich. Hier steht stattdessen,
     * was der jeweilige Fall braucht: mit oder ohne Adresse, mit der Prüfsumme
     * des Archivs, das der eigene Server ausliefert.
     */
    private static Properties manifest(String baseUrl, String sha256) {
        Properties values = new Properties();
        values.setProperty("runtime.version", "0.0-probe");
        values.setProperty("runtime.base-url", baseUrl);
        values.setProperty("runtime." + RuntimeManifest.platform() + ".archive", "probe.tar.gz");
        values.setProperty("runtime." + RuntimeManifest.platform() + ".sha256", sha256);
        return values;
    }

    // ---- Die Suchreihenfolge ------------------------------------------------

    @Test
    @DisplayName("Ein gesetztes fn.runtime.dir gewinnt, wenn etwas darin liegt")
    void configuredDirectoryWins(@TempDir Path dir, @TempDir Path game) throws IOException {
        Files.createFile(dir.resolve("jcef.jar"));
        System.setProperty("fn.runtime.dir", dir.toString());

        assertEquals(dir.toFile(), RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("Ein gesetztes fn.runtime.dir ohne Inhalt nennt den Bauweg")
    void configuredButEmptyDirectorySaysHow(@TempDir Path dir, @TempDir Path game) {
        System.setProperty("fn.runtime.dir", dir.toString());

        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));

        assertEquals(WebRuntimeState.RUNTIME_MISSING, thrown.status().state());
        assertTrue(thrown.status().reason().contains("build-jcef.ps1"),
                "wer den Ordner selbst gesetzt hat, will den Bauweg lesen: "
                        + thrown.status().reason());
    }

    @Test
    @DisplayName("Sonst zählt das Zwischenlager im Spielordner")
    void cacheIsUsedWhenComplete(@TempDir Path game) throws IOException {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();
        Files.createDirectories(cache);
        Files.createFile(cache.resolve("jcef.jar"));

        assertEquals(cache.toFile(), RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("Ein halb ausgepacktes Zwischenlager zählt nicht")
    void halfUnpackedCacheDoesNotCount(@TempDir Path game) throws IOException {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();
        Files.createDirectories(cache);
        Files.createFile(cache.resolve("jcef.jar"));
        // Genau der Zustand, den ein Absturz beim Auspacken hinterlässt: Die
        // Datei, an der die Suche einen fertigen Ordner erkennt, liegt schon
        // da — der Rest fehlt.
        Files.writeString(cache.resolve(".unvollstaendig"), "abgebrochen");
        RuntimeManifest.useForTests(manifest("", "0".repeat(64)));

        assertFalse(RuntimeInstall.complete(cache.toFile()));
        assertThrows(WebRuntimeUnavailable.class, () -> RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("Die Fassung steht im Pfad des Zwischenlagers")
    void cachePathCarriesTheVersion(@TempDir Path game) {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();

        assertEquals(RuntimeManifest.version(), cache.getFileName().toString(),
                "ohne die Fassung im Pfad liefe nach einem CEF-Wechsel der alte Stand weiter");
    }

    @Test
    @DisplayName("Ohne hinterlegte Adresse wird nichts geladen")
    void withoutAnAddressNothingIsFetched(@TempDir Path game) {
        RuntimeManifest.useForTests(manifest("", "0".repeat(64)));

        // So steht es heute im Manifest: base-url ist leer, solange keine
        // öffentliche Adresse existiert.
        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));

        assertEquals(WebRuntimeState.RUNTIME_MISSING, thrown.status().state());
        assertFalse(RuntimeInstall.downloading(), "es darf kein Thread gestartet worden sein");
    }

    // ---- Laden, prüfen, auspacken -------------------------------------------

    @Test
    @DisplayName("Mit Adresse läuft der Download im Hintergrund, und der zweite Blick findet den Ordner")
    void withAnAddressTheFetchRunsInTheBackground(@TempDir Path game) throws Exception {
        byte[] archive = tarGzWith("jcef.jar", "inhalt");
        String url = serve(archive);
        // Die Adresse ohne den Dateinamen: locate hängt ihn selbst an.
        RuntimeManifest.useForTests(manifest(url.substring(0, url.lastIndexOf('/')), sha256(archive)));

        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));
        assertEquals(WebRuntimeState.NOT_DOWNLOADED, thrown.status().state());

        // Nicht auf Verdacht schlafen, sondern warten, bis der Thread fertig
        // ist — mit einer Frist, damit ein hängender Download den Prüflauf
        // nicht mitnimmt.
        long deadline = System.currentTimeMillis() + 10_000;
        while (RuntimeInstall.downloading() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(RuntimeInstall.downloading(), "der Download muss in zehn Sekunden durch sein");
        assertEquals(RuntimeInstall.cacheDir(game.toFile()), RuntimeInstall.locate(game.toFile()),
                "der zweite Blick findet, was der erste angestoßen hat");
    }

    @Test
    @DisplayName("Ein Archiv wird geladen, geprüft und ausgepackt")
    void anArchiveIsFetchedCheckedAndUnpacked(@TempDir Path game) throws Exception {
        byte[] archive = tarGzWith("jcef.jar", "nicht wirklich eine Bibliothek");
        String url = serve(archive);
        Path target = game.resolve("ziel");

        RuntimeInstall.download(entry(sha256(archive)), url, target);

        assertTrue(RuntimeInstall.complete(target.toFile()), "der Ordner muss fertig sein");
        assertFalse(Files.exists(target.resolve(".unvollstaendig")), "der Marker muss weg sein");
        assertFalse(Files.exists(target.resolveSibling("probe.tar.gz.teil")),
                "die Teildatei muss weg sein");
    }

    @Test
    @DisplayName("Bei falscher Prüfsumme entsteht kein Ordner")
    void aWrongChecksumLeavesNothingBehind(@TempDir Path game) throws Exception {
        byte[] archive = tarGzWith("jcef.jar", "inhalt");
        String url = serve(archive);
        Path target = game.resolve("ziel");
        String wrong = "0".repeat(64);

        IOException thrown = assertThrows(IOException.class,
                () -> RuntimeInstall.download(entry(wrong), url, target));

        assertTrue(thrown.getMessage().contains("Prüfsumme"), thrown.getMessage());
        assertFalse(Files.exists(target), "ein Archiv, dem nicht zu trauen ist, wird nicht ausgepackt");
        assertFalse(Files.exists(target.resolveSibling("probe.tar.gz.teil")),
                "und die Teildatei bleibt nicht liegen");
    }

    @Test
    @DisplayName("Eine Adresse, die nicht antwortet, nennt ihren Rückgabewert")
    void aBadResponseSaysItsCode(@TempDir Path game) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/probe.tar.gz";

        IOException thrown = assertThrows(IOException.class,
                () -> RuntimeInstall.download(entry("0".repeat(64)), url, game.resolve("ziel")));

        assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
    }

    // ---- Hilfsmittel --------------------------------------------------------

    private static RuntimeManifest.Entry entry(String sha256) {
        return new RuntimeManifest.Entry("windows-x86_64", "probe.tar.gz", sha256);
    }

    /** Startet einen Server, der genau dieses Archiv ausliefert. */
    private String serve(byte[] archive) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe.tar.gz", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(archive);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/probe.tar.gz";
    }

    /** Baut ein echtes tar.gz mit einer Datei darin — über dasselbe tar. */
    private static byte[] tarGzWith(String name, String content) throws Exception {
        Path work = Files.createTempDirectory("fn-tar");
        try {
            Files.writeString(work.resolve(name), content);
            Path archive = Files.createTempFile("fn-probe", ".tar.gz");
            Process tar = new ProcessBuilder("tar", "-czf", archive.toAbsolutePath().toString(),
                    "-C", work.toAbsolutePath().toString(), ".")
                    .redirectErrorStream(true)
                    .start();
            if (tar.waitFor() != 0) {
                throw new IllegalStateException(
                        "tar scheiterte: " + new String(tar.getInputStream().readAllBytes()));
            }
            byte[] bytes = Files.readAllBytes(archive);
            Files.delete(archive);
            return bytes;
        } finally {
            try (var paths = Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Ein liegengebliebener Prüfordner ist kein Fehlschlag.
                    }
                });
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
