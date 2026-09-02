package dev.devpanda.bamboocef.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The web runtime does not know this mod.
 *
 * <p><b>This is the one rule that keeps the later cut cheap.</b> The
 * runtime is meant to become its own module one day — perhaps even its
 * own mod that others use. Whether that works is not decided on the day
 * of separation, but on every day before it: a single import of
 * {@code lang} or {@code network}, and the separation turns into a
 * cleanup operation.
 *
 * <p>That is why the rule stands as a test run and not as an intention in a
 * document. It holds in one direction: the mod may use the runtime, the
 * runtime not the mod.
 *
 * <p><b>No identifier either, no logger name, no constant.</b> What the
 * runtime must know about its host is passed to it. An exception
 * „just for the mod name" would be the first of many — and the reason why
 * such boundaries otherwise fall apart.
 */
class PackageBoundaryTest {

    private static final Path ROOT =
            Path.of("src/main/java/dev/devpanda/bamboocef/web");

    /** The mod's package, from which nothing may come in — except the runtime itself. */
    private static final String MOD = "dev.devpanda.bamboocef.";
    private static final String OWN = "dev.devpanda.bamboocef.web.";

    private static List<Path> sources() throws IOException {
        try (Stream<Path> found = Files.walk(ROOT)) {
            return found.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("The web runtime's package exists")
    void theRuntimePackageExists() {
        assertTrue(Files.isDirectory(ROOT),
                "ohne das Paket prüft dieser Lauf nichts — er wäre grün und wertlos");
    }

    @Test
    @DisplayName("Nothing in the runtime imports anything from the mod")
    void theRuntimeImportsNothingFromTheMod() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : sources()) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (!line.startsWith("import ")) {
                    continue;
                }
                String imported = line.substring("import ".length())
                        .replace("static ", "").replace(";", "").strip();
                if (imported.startsWith(MOD) && !imported.startsWith(OWN)) {
                    breaches.add("%s:%d — %s".formatted(
                            ROOT.relativize(source), i + 1, imported));
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "Die Web-Runtime darf nichts aus der Mod kennen:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("And not via the full name either, bypassing the import")
    void norByFullyQualifiedName() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : sources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            // Lines with a package or import declaration do not count; what is sought is the
            // reach in the middle of the code that bypasses an import.
            int from = 0;
            while (true) {
                int at = text.indexOf(MOD, from);
                if (at < 0) {
                    break;
                }
                from = at + MOD.length();
                if (text.startsWith(OWN, at)) {
                    continue;
                }
                int lineStart = text.lastIndexOf('\n', at) + 1;
                String line = text.substring(lineStart,
                        Math.max(lineStart, text.indexOf('\n', at))).strip();
                if (line.startsWith("package ") || line.startsWith("import ")
                        || line.startsWith("*") || line.startsWith("//")) {
                    continue;
                }
                breaches.add(ROOT.relativize(source) + " — " + line);
            }
        }
        assertTrue(breaches.isEmpty(),
                "Ein voll ausgeschriebener Name ist derselbe Bruch wie ein Import:\n  "
                        + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("The runtime also does not hang on Minecraft windows or blocks")
    void theRuntimeStaysOutOfGameplay() throws IOException {
        // What the runtime may need from Minecraft is the client: texture,
        // window size, keyboard. What it must not touch is the world —
        // otherwise a browser runtime becomes a mod again.
        List<String> forbidden = List.of(
                "net.minecraft.world.", "net.minecraft.server.");
        // <b>One exception, and it is not a gap.</b> ResourceManager lies
        // under net.minecraft.server.packs, but belongs to the loading of
        // resources and not to the server — the client has one too. The
        // runtime needs it for the signature of an inherited method that
        // does nothing. Written out and not as a pattern: what stands here
        // stands alone and stands out at the next glance.
        List<String> allowed = List.of("net.minecraft.server.packs.resources.ResourceManager");
        List<String> breaches = new ArrayList<>();
        for (Path source : sources()) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (String line : lines) {
                String stripped = line.strip();
                if (!stripped.startsWith("import ")) {
                    continue;
                }
                if (allowed.stream().anyMatch(stripped::contains)) {
                    continue;
                }
                for (String bad : forbidden) {
                    if (stripped.contains(bad)) {
                        breaches.add(ROOT.relativize(source) + " — " + stripped);
                    }
                }
            }
        }
        assertTrue(breaches.isEmpty(),
                "Die Runtime greift in das Spiel:\n  " + String.join("\n  ", breaches));
    }

    @Test
    @DisplayName("Conversely, the mod may use the runtime")
    void theModMayUseTheRuntime() {
        // Not a check, but a commitment: the rule holds in one
        // direction. If it did not stand here, the next person would read it as a ban
        // on every connection — and build a second runtime alongside.
        assertFalse(OWN.isEmpty());
    }
}
