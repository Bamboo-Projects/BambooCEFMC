package dev.devpanda.bamboocef;

import net.neoforged.fml.common.Mod;

/**
 * BambooCEF — the CEF-based web runtime as a standalone mod.
 *
 * <p>This is where what still lives in FactoryNetwork moves in: starting and
 * loading Chromium, the frame path, the input handling, and the public Java
 * API ({@code FnWeb}, {@code WebSurface}, {@code WebOverlay}, {@code WorldSurface}).
 * FactoryNetwork then becomes a consumer of this mod like any other.
 *
 * <p>Client only: a windowless Chromium has no place on a server.
 */
@Mod(BambooCEF.MOD_ID)
public final class BambooCEF {

    public static final String MOD_ID = "bamboocef";

    public BambooCEF() {
    }
}
