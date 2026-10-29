package dev.devpanda.bamboocef;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * BambooCEF — the CEF-based web runtime as a standalone mod.
 *
 * <p>Starting and loading Chromium, the frame path, the input handling, and
 * the public Java API ({@code FnWeb}, {@code WebSurface}, {@code WebOverlay},
 * {@code WorldSurface}) live here. Other mods are consumers of this one.
 *
 * <p>Client only: a windowless Chromium has no place on a server.
 */
@Mod(BambooCEF.MOD_ID)
public final class BambooCEF {

    public static final String MOD_ID = "bamboocef";

    public BambooCEF() {
        // <b>The earliest Java code this mod has.</b> Chromium can start as
        // early as the very first screen switch — and that comes during
        // loading, so before every setup event. Anyone who wants to add to
        // Chromium's command line has to do it here or not at all.
        //
        // Only does anything when bamboocef.devtools is set; on a server the class
        // does not even exist in the first place.
        if (FMLEnvironment.getDist().isClient()) {
            dev.devpanda.bamboocef.web.runtime.WebDebug.requestIfEnabled();
        }
    }
}
