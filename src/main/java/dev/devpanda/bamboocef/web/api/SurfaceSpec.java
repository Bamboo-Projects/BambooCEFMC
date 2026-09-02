package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.BrowserVisibility;

/**
 * Der Bauplan einer Fläche.
 *
 * <p><b>Ein Bauplan und keine Parameterliste.</b> Sechs Angaben, von denen
 * vier fast immer dasselbe sind — als Parameter wären das sechs Zahlen in
 * einer Zeile, bei denen niemand mehr weiß, welche die Höhe ist. Und jede
 * spätere Angabe bräche jeden Aufruf.
 *
 * <pre>
 * SurfaceSpec.of("https://example.org", 512, 512)
 *     .named("Schnellmenü")
 *     .keys(KeyFilter.only(GLFW.GLFW_KEY_ESCAPE))
 * </pre>
 *
 * @param url        was geladen wird
 * @param width      Breite in Pixeln
 * @param height     Höhe in Pixeln
 * @param name       wie die Fläche im Protokoll und in Chromiums Liste heißt
 * @param keys       welche Tasten sie bekommt
 * @param visibility wie oft sie malen darf
 * @param transparent ob der Grund durchscheint
 */
public record SurfaceSpec(String url, int width, int height, String name,
                          KeyFilter keys, BrowserVisibility visibility,
                          boolean transparent) {

    /** Die größte Kantenlänge, die zugelassen wird. */
    public static final int MAX_EDGE = 4096;

    public SurfaceSpec {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Eine Fläche ohne Adresse hat nichts zu zeigen");
        }
        // <b>Begrenzt, und zwar hier.</b> Eine Fläche entsteht aus einer Zahl,
        // die irgendwo herkommt; eine Null ergäbe einen Browser, der nie malt,
        // und eine sehr große einen, der den Speicher der Grafikkarte frisst.
        // Beides fällt erst viel später auf.
        width = Math.clamp(width, 1, MAX_EDGE);
        height = Math.clamp(height, 1, MAX_EDGE);
        name = name == null ? "" : name;
        keys = keys == null ? KeyFilter.NONE : keys;
        visibility = visibility == null ? BrowserVisibility.ACTIVE : visibility;
    }

    /**
     * Das Übliche: eine Adresse und eine Größe.
     *
     * <p>Ohne Namen, ohne Tasten, deckend, mit mittlerem Takt. Wer mehr will,
     * hängt es an.
     */
    public static SurfaceSpec of(String url, int width, int height) {
        return new SurfaceSpec(url, width, height, "", KeyFilter.NONE,
                BrowserVisibility.ACTIVE, false);
    }

    public SurfaceSpec named(String name) {
        return new SurfaceSpec(url, width, height, name, keys, visibility, transparent);
    }

    public SurfaceSpec keys(KeyFilter keys) {
        return new SurfaceSpec(url, width, height, name, keys, visibility, transparent);
    }

    public SurfaceSpec visibility(BrowserVisibility visibility) {
        return new SurfaceSpec(url, width, height, name, keys, visibility, transparent);
    }

    /**
     * Ob der Grund durchscheint.
     *
     * <p>Kostet nichts an Rechenzeit, aber alles an Deutlichkeit: Eine
     * durchsichtige Fläche vor einer hellen Wand ist nicht mehr zu lesen. Für
     * ein Overlay richtig, für eine Tafel selten.
     *
     * <p>Mit Parameter und nicht ohne: {@code transparent()} gehört schon dem
     * Datensatz — es liest die Angabe, statt sie zu setzen.
     */
    public SurfaceSpec transparent(boolean on) {
        return new SurfaceSpec(url, width, height, name, keys, visibility, on);
    }
}
