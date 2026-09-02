package dev.devpanda.bamboocef;

import net.neoforged.fml.common.Mod;

/**
 * BambooCEF — die CEF-basierte Web-Runtime als eigenständige Mod.
 *
 * <p>Hier zieht ein, was heute noch in FactoryNetwork liegt: das Starten und
 * Laden von Chromium, der Bildweg, die Eingaben, und die öffentliche Java-API
 * ({@code FnWeb}, {@code WebSurface}, {@code WebOverlay}, {@code WorldSurface}).
 * FactoryNetwork wird dann ein Nutzer dieser Mod wie jede andere.
 *
 * <p>Nur Client: Ein fensterloses Chromium gibt es auf einem Server nicht.
 */
@Mod(BambooCEF.MOD_ID)
public final class BambooCEF {

    public static final String MOD_ID = "bamboocef";

    public BambooCEF() {
    }
}
