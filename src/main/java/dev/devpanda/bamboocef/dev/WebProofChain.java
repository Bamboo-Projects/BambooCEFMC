package dev.devpanda.bamboocef.dev;

import dev.devpanda.bamboocef.web.runtime.WebBenchmark;
import dev.devpanda.bamboocef.web.runtime.WebSelfTest;
import dev.devpanda.bamboocef.web.screen.BackdropBenchmark;
import dev.devpanda.bamboocef.web.screen.BackdropProof;
import dev.devpanda.bamboocef.web.screen.InteractionBenchmark;
import dev.devpanda.bamboocef.web.screen.RenderProof;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the proofs into an order that does not disturb itself.
 *
 * <p>Three runs want to use the same Chromium:
 *
 * <ol>
 *   <li>The <b>texture self-test</b> — does the picture reach the texture correctly?</li>
 *   <li>The <b>render proof</b> — does it reach the screen correctly?</li>
 *   <li>The <b>benchmark</b> — what does it cost?</li>
 * </ol>
 *
 * <p>None of them would work at the same time: two browsers share the frame
 * rate, and every benchmark would report half as the limit. The render proof
 * additionally needs a screen all to itself.
 *
 * <p>Runs only when {@code bamboocef.benchmark} is set. In ordinary play no one gains
 * anything from a test image popping up when entering the world. By hand it
 * works any time with {@code /bamboocef nachweis}.
 *
 * <p><b>Development tooling, not shipped behavior.</b> This class reaches into
 * the runtime's internals on purpose — that is why it lives here beside the
 * runtime and not in a consuming mod, which only ever sees {@code web.api}.
 */
public final class WebProofChain {

    /** A short pause between two browsers — two seconds. */
    private static final int SETTLE_TICKS = 40;

    private static final boolean ENABLED = Boolean.getBoolean("bamboocef.benchmark");

    /**
     * A run that only wants an editor skips the chain.
     *
     * <p>The proofs A through E together need over three minutes before the
     * first page appears. For a spike that needs several runs, that is a lost
     * quarter of an hour every time — so {@code fn.ide} wins over
     * {@code bamboocef.benchmark}. What such a run opens instead is the consuming
     * mod's business, not this one's.
     */
    private static final boolean IDE_ONLY = Boolean.getBoolean("fn.ide");

    private static final Logger LOG = LoggerFactory.getLogger("BambooCEF/WebProofChain");

    private static boolean proofStarted;
    private static boolean backdropStarted;
    private static boolean backdropMeasured;
    private static boolean interactionStarted;
    private static int ticksWaiting;

    private WebProofChain() {
    }

    /**
     * Whether the chain wants to run at all.
     *
     * <p>The client asks before it lets the self-test start. The chain needs
     * the self-test as its first step, and without someone asking for it the
     * library leaves Chromium down — a library must not boot a browser on its
     * own.
     */
    public static boolean wanted() {
        return ENABLED && !IDE_ONLY;
    }

    /** One step per client tick; does nothing unless {@link #wanted()}. */
    public static void tick() {
        if (!wanted()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // Without a world there is no meaningful background and the frame rate
        // is capped; the benchmark waits for it anyway.
        if (client.level == null || !WebSelfTest.finished()) {
            return;
        }
        ticksWaiting++;
        if (ticksWaiting < SETTLE_TICKS) {
            return;
        }
        if (!proofStarted) {
            proofStarted = true;
            ticksWaiting = 0;
            RenderProof.openIfPossible(client);
            return;
        }
        if (!backdropStarted && RenderProof.finished()) {
            backdropStarted = true;
            ticksWaiting = 0;
            BackdropProof.openIfPossible(client);
            return;
        }
        // The backdrop benchmark right after its proof: it needs the screen to
        // itself, and what comes after needs it just as much.
        if (!backdropMeasured && BackdropProof.finished()) {
            backdropMeasured = true;
            ticksWaiting = 0;
            try {
                BackdropBenchmark.open(client);
            } catch (Exception broken) {
                LOG.warn("Hintergrundmessung ließ sich nicht öffnen", broken);
            }
            return;
        }
        // The interaction benchmark very last: it holds the screen to itself,
        // and the numbers benchmark before it needs it free.
        if (!interactionStarted && BackdropBenchmark.finished() && WebBenchmark.finished()) {
            interactionStarted = true;
            try {
                InteractionBenchmark.open(client);
            } catch (Exception broken) {
                LOG.warn("Interaktionsmessung ließ sich nicht öffnen", broken);
            }
        }
    }
}
