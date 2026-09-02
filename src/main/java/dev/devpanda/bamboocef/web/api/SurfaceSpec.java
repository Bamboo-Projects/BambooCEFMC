package dev.devpanda.bamboocef.web.api;

import dev.devpanda.bamboocef.web.BrowserVisibility;

/**
 * The blueprint of a surface.
 *
 * <p><b>A blueprint, not a parameter list.</b> Six inputs, four of which are
 * almost always the same — as parameters those would be six numbers in a row
 * where no one can tell anymore which one is the height. And every later
 * input would break every call.
 *
 * <pre>
 * SurfaceSpec.of("https://example.org", 512, 512)
 *     .named("Schnellmenü")
 *     .keys(KeyFilter.only(GLFW.GLFW_KEY_ESCAPE))
 * </pre>
 *
 * @param url        what is loaded
 * @param width      width in pixels
 * @param height     height in pixels
 * @param name       what the surface is called in the log and in Chromium's list
 * @param keys       which keys it gets
 * @param visibility how often it may draw
 * @param transparent whether the background shows through
 */
public record SurfaceSpec(String url, int width, int height, String name,
                          KeyFilter keys, BrowserVisibility visibility,
                          boolean transparent) {

    /** The largest edge length that is allowed. */
    public static final int MAX_EDGE = 4096;

    public SurfaceSpec {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Eine Fläche ohne Adresse hat nichts zu zeigen");
        }
        // <b>Bounded, and right here.</b> A surface arises from a number that
        // comes from somewhere; a zero would yield a browser that never draws,
        // and a very large one a browser that eats the graphics card's memory.
        // Both only show up much later.
        width = Math.clamp(width, 1, MAX_EDGE);
        height = Math.clamp(height, 1, MAX_EDGE);
        name = name == null ? "" : name;
        keys = keys == null ? KeyFilter.NONE : keys;
        visibility = visibility == null ? BrowserVisibility.ACTIVE : visibility;
    }

    /**
     * The usual: an address and a size.
     *
     * <p>Without a name, without keys, opaque, at a medium cadence. Whoever
     * wants more appends it.
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
     * Whether the background shows through.
     *
     * <p>Costs nothing in compute time, but everything in clarity: a
     * transparent surface in front of a bright wall can no longer be read. Right
     * for an overlay, rarely for a board.
     *
     * <p>With a parameter and not without: {@code transparent()} already
     * belongs to the record — it reads the setting instead of setting it.
     */
    public SurfaceSpec transparent(boolean on) {
        return new SurfaceSpec(url, width, height, name, keys, visibility, on);
    }
}
