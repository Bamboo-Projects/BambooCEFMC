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
 * Where the runtime environment is searched for and how it is fetched.
 *
 * <p><b>Why these test runs are more important than most.</b> In the
 * development run {@code bamboocef.runtime.dir} is set, and with that the
 * search ends in the first line. Everything behind it — cache, download,
 * checksum, half-unpacked folder — is <b>never</b> touched during
 * development and <b>always</b> at the player's.
 *
 * <p>The server is one from the Java library and runs on a port
 * that the operating system picks. No network, no foreign address, no
 * waiting time.
 */
class RuntimeInstallTest {

    private HttpServer server;

    @AfterEach
    void cleanUp() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        System.clearProperty("bamboocef.runtime.dir");
        RuntimeManifest.useForTests(null);
    }

    /**
     * A manifest for the test run.
     *
     * <p>The real one in the classpath names an address since delivery —
     * whoever calls {@code locate} with it really downloads. Here stands instead
     * what the respective case needs: with or without an address, with the checksum
     * of the archive that the own server delivers.
     */
    private static Properties manifest(String baseUrl, String sha256) {
        Properties values = new Properties();
        values.setProperty("runtime.version", "0.0-probe");
        values.setProperty("runtime.base-url", baseUrl);
        values.setProperty("runtime." + RuntimeManifest.platform() + ".archive", "probe.tar.gz");
        values.setProperty("runtime." + RuntimeManifest.platform() + ".sha256", sha256);
        return values;
    }

    // ---- The search order ---------------------------------------------------

    @Test
    @DisplayName("A set bamboocef.runtime.dir wins if something lies in it")
    void configuredDirectoryWins(@TempDir Path dir, @TempDir Path game) throws IOException {
        Files.createFile(dir.resolve("jcef.jar"));
        System.setProperty("bamboocef.runtime.dir", dir.toString());

        assertEquals(dir.toFile(), RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("A set bamboocef.runtime.dir with no content names the build path")
    void configuredButEmptyDirectorySaysHow(@TempDir Path dir, @TempDir Path game) {
        System.setProperty("bamboocef.runtime.dir", dir.toString());

        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));

        assertEquals(WebRuntimeState.RUNTIME_MISSING, thrown.status().state());
        assertTrue(thrown.status().reason().contains("build-jcef.ps1"),
                "wer den Ordner selbst gesetzt hat, will den Bauweg lesen: "
                        + thrown.status().reason());
    }

    @Test
    @DisplayName("Otherwise the cache in the game folder counts")
    void cacheIsUsedWhenComplete(@TempDir Path game) throws IOException {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();
        Files.createDirectories(cache);
        Files.createFile(cache.resolve("jcef.jar"));

        assertEquals(cache.toFile(), RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("A half-unpacked cache does not count")
    void halfUnpackedCacheDoesNotCount(@TempDir Path game) throws IOException {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();
        Files.createDirectories(cache);
        Files.createFile(cache.resolve("jcef.jar"));
        // Exactly the state that a crash during unpacking leaves behind: the
        // file by which the search recognizes a finished folder already lies
        // there — the rest is missing.
        Files.writeString(cache.resolve(".unvollstaendig"), "abgebrochen");
        RuntimeManifest.useForTests(manifest("", "0".repeat(64)));

        assertFalse(RuntimeInstall.complete(cache.toFile()));
        assertThrows(WebRuntimeUnavailable.class, () -> RuntimeInstall.locate(game.toFile()));
    }

    @Test
    @DisplayName("The version stands in the cache's path")
    void cachePathCarriesTheVersion(@TempDir Path game) {
        Path cache = RuntimeInstall.cacheDir(game.toFile()).toPath();

        assertEquals(RuntimeManifest.version(), cache.getFileName().toString(),
                "ohne die Fassung im Pfad liefe nach einem CEF-Wechsel der alte Stand weiter");
    }

    @Test
    @DisplayName("Without a stored address nothing is fetched")
    void withoutAnAddressNothingIsFetched(@TempDir Path game) {
        RuntimeManifest.useForTests(manifest("", "0".repeat(64)));

        // This is how it stands in the manifest today: base-url is empty as long as no
        // public address exists.
        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));

        assertEquals(WebRuntimeState.RUNTIME_MISSING, thrown.status().state());
        assertFalse(RuntimeInstall.downloading(), "es darf kein Thread gestartet worden sein");
    }

    // ---- Fetch, check, unpack -----------------------------------------------

    @Test
    @DisplayName("With an address the download runs in the background, and the second look finds the folder")
    void withAnAddressTheFetchRunsInTheBackground(@TempDir Path game) throws Exception {
        byte[] archive = tarGzWith("jcef.jar", "inhalt");
        String url = serve(archive);
        // The address without the file name: locate appends it itself.
        RuntimeManifest.useForTests(manifest(url.substring(0, url.lastIndexOf('/')), sha256(archive)));

        WebRuntimeUnavailable thrown = assertThrows(WebRuntimeUnavailable.class,
                () -> RuntimeInstall.locate(game.toFile()));
        assertEquals(WebRuntimeState.NOT_DOWNLOADED, thrown.status().state());

        // Do not sleep on suspicion, but wait until the thread is
        // done — with a deadline, so that a hanging download does not take the test run
        // down with it.
        long deadline = System.currentTimeMillis() + 10_000;
        while (RuntimeInstall.downloading() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(RuntimeInstall.downloading(), "der Download muss in zehn Sekunden durch sein");
        assertEquals(RuntimeInstall.cacheDir(game.toFile()), RuntimeInstall.locate(game.toFile()),
                "der zweite Blick findet, was der erste angestoßen hat");
    }

    @Test
    @DisplayName("An archive is fetched, checked and unpacked")
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
    @DisplayName("With a wrong checksum no folder comes into being")
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
    @DisplayName("An address that does not answer names its return code")
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

    // ---- Helpers ------------------------------------------------------------

    private static RuntimeManifest.Entry entry(String sha256) {
        return new RuntimeManifest.Entry("windows-x86_64", "probe.tar.gz", sha256);
    }

    /** Starts a server that delivers exactly this archive. */
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

    /** Builds a real tar.gz with a file in it — via the same tar. */
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
                        // A leftover test folder is not a failure.
                    }
                });
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
