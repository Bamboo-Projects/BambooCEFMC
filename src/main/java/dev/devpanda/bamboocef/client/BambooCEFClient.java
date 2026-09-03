package dev.devpanda.bamboocef.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devpanda.bamboocef.BambooCEF;
import dev.devpanda.bamboocef.dev.WebProofChain;
import dev.devpanda.bamboocef.web.BrowserManager;
import dev.devpanda.bamboocef.web.WebRuntime;
import dev.devpanda.bamboocef.web.api.Overlays;
import dev.devpanda.bamboocef.web.api.WorldSurfaces;
import dev.devpanda.bamboocef.web.runtime.WebBenchmark;
import dev.devpanda.bamboocef.web.runtime.WebPump;
import dev.devpanda.bamboocef.web.runtime.WebSelfTest;
import dev.devpanda.bamboocef.web.runtime.WebWarmup;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * The heartbeat of the web runtime, hung into the client's events.
 *
 * <p>Everything the runtime needs from the running game sits here and only
 * here: the beat that pumps Chromium, the two drawing passes, and the two
 * shutdowns. The runtime itself knows no events — it is driven from outside,
 * which is what makes it testable without a game.
 *
 * <p>Client only. A windowless Chromium has no place on a server, and the
 * class is therefore never loaded there.
 */
@EventBusSubscriber(modid = BambooCEF.MOD_ID, value = Dist.CLIENT)
public final class BambooCEFClient {

    /** {@code -Dbamboocef.selftest=true} starts and checks Chromium shortly after launch. */
    private static final boolean SELF_TEST = Boolean.getBoolean("bamboocef.selftest");

    /**
     * The runtime's tick — twenty times a second.
     *
     * <p>The order is deliberate. The self-test of the image path runs first
     * and once per session; it needs a drawing context and can therefore not
     * be an ordinary check run. The proof chain follows, because the benchmark
     * after it waits for the proofs to be through. The benchmark itself runs
     * only on request: {@code -Dbamboocef.benchmark=true}. All three would otherwise
     * share a Chromium with the surfaces and measure each other.
     */
    @SubscribeEvent
    public static void tickWebRuntime(ClientTickEvent.Post event) {
        // A library must not start Chromium on its own: the self-test runs only
        // when asked for. Consumers get the runtime lazily, on the first surface.
        //
        // The proof chain counts as an asker. It needs the self-test as its
        // first step, and without this line a run with -Dbamboocef.benchmark would
        // wait forever for a self-test that nobody started.
        if (SELF_TEST || WebProofChain.wanted()) {
            WebSelfTest.tick();
        }
        // Only acts once a consumer asked via FnWeb.prepare().
        WebWarmup.tick();
        WebProofChain.tick();
        WebBenchmark.tick();
        Overlays.tick();
        WorldSurfaces.tick();
    }

    /** Overlays on top of the picture — after everything Minecraft paints there. */
    @SubscribeEvent
    public static void drawOverlays(RenderGuiEvent.Post event) {
        Overlays.draw(event.getGuiGraphics());
    }

    /**
     * Surfaces in the world — after the translucent blocks, so that water and
     * glass stay in front and the surface does not shine through them.
     */
    @SubscribeEvent
    public static void drawWorldSurfaces(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        if (pose == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        WorldSurfaces.draw(pose, buffers, camera.x, camera.y, camera.z);
    }

    /**
     * The gap between two frames — the only number a player notices.
     *
     * <p>On the frame and not on the tick: the tick runs at a fixed twenty per
     * second and knows nothing of whether the drawing stutters in between.
     */
    @SubscribeEvent
    public static void measureFrame(RenderFrameEvent.Post event) {
        WebBenchmark.frameRendered();
    }

    /**
     * Chromium's message loop, once per frame.
     *
     * <p><b>Before the drawing and not after.</b> What Chromium delivers in
     * this round should still make it into the texture this frame uses —
     * otherwise the page lags one frame behind.
     *
     * <p>Pumping happens behind {@code WebPump}. The spot was once without
     * consequence: as long as MCEF provided the underpinnings, its own mixin
     * did it. Since the runtime belongs to us, there is no mixin any more, and
     * the beat comes from exactly this line.
     */
    @SubscribeEvent
    public static void pumpWebRuntime(RenderFrameEvent.Pre event) {
        WebPump.frame();
    }

    /**
     * When leaving a world the browsers close — Chromium stays.
     *
     * <p><b>A full shutdown once stood here, and that was a mistake.</b> CEF
     * can be started exactly once in a process; a second attempt ends with
     * "Settings can only be passed to CEF before createClient is called the
     * first time". Whoever left one world and entered another had no browser
     * for the rest of the session.
     *
     * <p>What belongs here are the browsers: they point at blocks of a world
     * that will be gone in a moment. Chromium itself only shuts down when the
     * game is quit, in {@link #shutDownWebRuntime}.
     */
    @SubscribeEvent
    public static void closeBrowsersOfThisWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        Overlays.closeAll();
        WorldSurfaces.closeAll();
        BrowserManager.closeAll();
    }

    /**
     * When the game is quit, Chromium shuts down.
     *
     * <p>And only here, because it works exactly once. The order sits in
     * {@code WebRuntime.shutdown()}: first ask all browsers to close, then pump
     * for their confirmation, then clean up. Whoever reverses it leaves helper
     * processes behind.
     */
    @SubscribeEvent
    public static void shutDownWebRuntime(GameShuttingDownEvent event) {
        WorldSurfaces.closeAll();
        WebRuntime.shutdown();
    }

    private BambooCEFClient() {
    }
}
