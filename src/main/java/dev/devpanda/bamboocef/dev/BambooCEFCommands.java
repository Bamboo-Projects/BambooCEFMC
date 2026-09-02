package dev.devpanda.bamboocef.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.devpanda.bamboocef.BambooCEF;
import dev.devpanda.bamboocef.web.WebRuntimeStatus;
import dev.devpanda.bamboocef.web.capture.WorldCapture;
import dev.devpanda.bamboocef.web.screen.BackdropBenchmark;
import dev.devpanda.bamboocef.web.screen.BackdropProof;
import dev.devpanda.bamboocef.web.screen.BackdropScreen;
import dev.devpanda.bamboocef.web.screen.BrowserScreen;
import dev.devpanda.bamboocef.web.screen.InteractionBenchmark;
import dev.devpanda.bamboocef.web.screen.ProbePage;
import dev.devpanda.bamboocef.web.screen.RenderProof;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The runtime's own console — proofs, measurements and Chromium's tools.
 *
 * <p><b>Why these commands live here and not in a consuming mod.</b> Every one
 * of them reaches into the runtime's internals: a raw browser screen, the
 * render proof, the backdrop measurements, the remote-debugging list. A
 * consumer sees only {@code web.api} and could not open any of this without
 * tearing a hole into that boundary. So they stand beside the thing they
 * inspect.
 *
 * <p>Everything here is development tooling. It is not a control scheme, and
 * nothing in shipped play depends on it.
 */
@EventBusSubscriber(modid = BambooCEF.MOD_ID, value = Dist.CLIENT)
public final class BambooCEFCommands {

    private BambooCEFCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bamboocef")
                .then(Commands.literal("probe")
                        .executes(context -> {
                            openProbe();
                            return 1;
                        }))
                .then(Commands.literal("nachweis")
                        .executes(context -> {
                            later(() -> RenderProof.openIfPossible(Minecraft.getInstance()));
                            return 1;
                        }))
                .then(Commands.literal("seite")
                        .then(Commands.argument("adresse", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String url = StringArgumentType.getString(context, "adresse");
                                    later(() -> open(url, false));
                                    return 1;
                                })))
                .then(Commands.literal("hintergrund")
                        .executes(context -> {
                            later(() -> BackdropProof.openIfPossible(Minecraft.getInstance()));
                            return 1;
                        }))
                .then(Commands.literal("glas")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    BackdropScreen.open(
                                            Minecraft.getInstance(),
                                            BackdropScreen.Mode.LOW_5,
                                            0.5,
                                            // BMP, as the report recommends: the
                                            // encoding costs a two-hundredth of PNG.
                                            WorldCapture.Format.BMP);
                                } catch (Exception broken) {
                                    say("Hintergrund ließ sich nicht öffnen: " + broken.getMessage());
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("glasmessung")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    BackdropBenchmark.open(Minecraft.getInstance());
                                } catch (Exception broken) {
                                    say("Messung ließ sich nicht öffnen: " + broken.getMessage());
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("messung")
                        .executes(context -> {
                            later(() -> {
                                try {
                                    InteractionBenchmark.open(Minecraft.getInstance());
                                } catch (Exception broken) {
                                    say("Messung ließ sich nicht öffnen: " + broken.getMessage());
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("devtools")
                        .executes(context -> {
                            WebDevTools.show();
                            return 1;
                        }))
                .then(Commands.literal("zustand")
                        .executes(context -> {
                            WebRuntimeStatus status = BrowserScreen.availability();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Web-Runtime: " + status), false);
                            return 1;
                        })));
    }

    private static void openProbe() {
        later(() -> {
            try {
                open(ProbePage.url(), false);
            } catch (Exception broken) {
                say("Die Prüfseite ließ sich nicht ablegen: " + broken.getMessage());
            }
        });
    }

    private static void open(String url, boolean transparent) {
        WebRuntimeStatus status = BrowserScreen.availability();
        if (!status.usable()) {
            say("Kein Browser zu haben: " + status);
            return;
        }
        Minecraft.getInstance().setScreen(
                new BrowserScreen(Component.literal("Web"), url, transparent));
    }

    /**
     * Only run on the next pass.
     *
     * <p><b>Otherwise nothing happens.</b> A command runs while the console is
     * still open; Minecraft closes it immediately afterwards and sets the
     * screen to nothing in doing so. A screen that is set in the meantime is
     * gone again a tenth of a second later.
     */
    private static void later(Runnable what) {
        Minecraft.getInstance().execute(what);
    }

    private static void say(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
