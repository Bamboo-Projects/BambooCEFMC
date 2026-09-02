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
 * Die Web-Runtime kennt diese Mod nicht.
 *
 * <p><b>Das ist die eine Regel, die den späteren Schnitt billig hält.</b> Die
 * Runtime soll eines Tages ein eigenes Modul sein — vielleicht sogar eine
 * eigene Mod, die andere benutzen. Ob das geht, entscheidet sich nicht am Tag
 * der Trennung, sondern an jedem Tag davor: Ein einziger Import auf
 * {@code lang} oder {@code network}, und aus der Trennung wird eine
 * Aufräumaktion.
 *
 * <p>Deshalb steht die Regel als Prüflauf und nicht als Absicht in einem
 * Dokument. Sie gilt in eine Richtung: Die Mod darf die Runtime benutzen, die
 * Runtime die Mod nicht.
 *
 * <p><b>Auch keine Kennung, kein Logger-Name, keine Konstante.</b> Was die
 * Runtime von ihrem Wirt wissen muss, bekommt sie übergeben. Eine Ausnahme
 * „nur für den Modnamen" wäre die erste von vielen — und der Grund, warum
 * solche Grenzen sonst zerfallen.
 */
class PackageBoundaryTest {

    private static final Path ROOT =
            Path.of("src/main/java/dev/devpanda/bamboocef/web");

    /** Das Paket der Mod, aus dem nichts hereindarf — außer der Runtime selbst. */
    private static final String MOD = "dev.devpanda.bamboocef.";
    private static final String OWN = "dev.devpanda.bamboocef.web.";

    private static List<Path> sources() throws IOException {
        try (Stream<Path> found = Files.walk(ROOT)) {
            return found.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("Das Paket der Web-Runtime gibt es")
    void theRuntimePackageExists() {
        assertTrue(Files.isDirectory(ROOT),
                "ohne das Paket prüft dieser Lauf nichts — er wäre grün und wertlos");
    }

    @Test
    @DisplayName("Nichts in der Runtime importiert etwas aus der Mod")
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
    @DisplayName("Und auch nicht über den vollen Namen, am Import vorbei")
    void norByFullyQualifiedName() throws IOException {
        List<String> breaches = new ArrayList<>();
        for (Path source : sources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            // Zeilen mit Paket- oder Importangabe zählen nicht; gesucht ist der
            // Griff mitten im Code, mit dem man einen Import umgeht.
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
    @DisplayName("Die Runtime hängt auch nicht an Minecraft-Fenstern oder Blöcken")
    void theRuntimeStaysOutOfGameplay() throws IOException {
        // Was die Runtime von Minecraft brauchen darf, ist der Client: Textur,
        // Fenstergröße, Tastatur. Was sie nicht anfassen darf, ist die Welt —
        // sonst wird aus einer Browserlaufzeit wieder eine Mod.
        List<String> forbidden = List.of(
                "net.minecraft.world.", "net.minecraft.server.");
        // <b>Eine Ausnahme, und sie ist keine Lücke.</b> ResourceManager liegt
        // unter net.minecraft.server.packs, gehört aber zum Laden von
        // Ressourcen und nicht zum Server — auch der Client hat einen. Die
        // Runtime braucht ihn für die Signatur einer geerbten Methode, die
        // nichts tut. Ausgeschrieben und nicht als Muster: Was hier steht,
        // steht einzeln da und fällt beim nächsten Blick auf.
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
    @DisplayName("Umgekehrt darf die Mod die Runtime benutzen")
    void theModMayUseTheRuntime() {
        // Keine Prüfung, sondern eine Festlegung: Die Regel gilt in eine
        // Richtung. Stünde sie nicht hier, läse der nächste sie als Verbot
        // jeder Verbindung — und baute eine zweite Runtime daneben.
        assertFalse(OWN.isEmpty());
    }
}
