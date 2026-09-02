package dev.devpanda.bamboocef.web.view;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.IntSupplier;

/**
 * Eine Minecraft-Textur, die auf Chromiums Textur zeigt.
 *
 * <p><b>Wozu der Umweg.</b> Im Bildschirm lässt sich eine rohe GL-Kennung
 * direkt binden. In der Welt nicht: Dort läuft alles über {@code RenderType},
 * und der verlangt eine {@code ResourceLocation}. Diese Klasse ist die
 * Übersetzung dazwischen — angemeldet beim Texturverwalter, gefragt wird sie
 * bei jedem Binden.
 *
 * <p><b>Die Kennung wird nicht gemerkt, sondern geholt.</b> Ein
 * {@code this.id}, einmal gesetzt, wäre nach der ersten Größenänderung falsch
 * — Chromium legt dann eine neue Textur an. {@code getId()} fragt deshalb
 * jedes Mal nach.
 *
 * <p><b>Und sie wird nie gelöscht.</b> Die Textur gehört der Sitzung, nicht
 * dem Texturverwalter. Weil das geerbte Feld {@code id} unberührt auf minus
 * eins bleibt, räumt Minecraft ohnehin nichts ab; die beiden leeren Methoden
 * stehen trotzdem hier, damit die Absicht nicht von einem Zufall abhängt.
 */
public final class ManagedTexture extends AbstractTexture {

    private final IntSupplier live;

    public ManagedTexture(IntSupplier live) {
        this.live = live;
    }

    @Override
    public int getId() {
        return live.getAsInt();
    }

    @Override
    public void load(ResourceManager manager) {
        // Es gibt nichts zu laden: Die Bilder kommen aus Chromium. Damit
        // übersteht die Textur auch ein Neuladen der Ressourcen (F3+T).
    }

    @Override
    public void close() {
        // Sie gehört uns nicht.
    }

    @Override
    public void releaseId() {
        // Ebenso wenig.
    }
}
