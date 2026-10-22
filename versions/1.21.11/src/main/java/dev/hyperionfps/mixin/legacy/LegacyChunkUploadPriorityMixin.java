package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.chunk.ElytraFlightSmoother;
import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.core.ThoriumFeatureToggles;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;

/**
 * Defers the chunk section builds that the player has already flown past.
 *
 * <p>On the legacy pipeline a section's mesh is built by
 * {@code SectionRenderDispatcher$RenderSection#rebuildSectionAsync}, which creates the
 * compile task and hands it to the dispatcher. That is the point at which the
 * section's block position is known, and it is the last point at which the build can
 * still be postponed - which is exactly what the Elytra Flight Smoother needs.</p>
 *
 * <p>1.21.11 already carries the 26.x shape here: the dispatcher is reached through
 * the section itself, and the position is called {@code getRenderOrigin()}. 1.21.1
 * still passes the dispatcher in and calls the position {@code getOrigin()}, so the
 * two legacy nodes have their own copy of this hook.</p>
 *
 * <p>While the player is flying fast, a section that lies behind the flight vector is
 * held back by {@link ThoriumChunkUploadPacer} instead of being built right away. The
 * pacer releases it later, in the order the flight vector demands, so the world ahead
 * of the player is built first.</p>
 *
 * <p>The method is a pure scheduling call - it creates a task and queues it - so
 * calling it a few frames later is safe. The pacer additionally releases anything
 * that has waited for more than half a second, so a deferral can never turn into a
 * permanent hole in the world.</p>
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class LegacyChunkUploadPriorityMixin {

    /**
     * Whether this section is already waiting in Thorium's deferred queue.
     *
     * <p>Vanilla may ask for the same section again while it waits - the 26.x
     * pipeline in particular rebuilds its list of sections to update every frame.
     * The flag keeps one section from being queued twice.</p>
     */
    @Unique
    private boolean thorium$deferred;

    @Inject(method = "rebuildSectionAsync", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$deferSectionsBehindThePlayer(RenderRegionCache region, CallbackInfo ci) {
        // The release of a deferred section calls this method again; that call must
        // not be deferred a second time.
        if (ThoriumChunkUploadPacer.isReplaying()) {
            return;
        }
        if (!ThoriumChunkUploadPacer.isEnabled() || !ThoriumFeatureToggles.isElytraSmootherEnabled()) {
            return;
        }
        if (!ElytraFlightSmoother.isFastFlight()) {
            return;
        }

        SectionRenderDispatcher.RenderSection section = (SectionRenderDispatcher.RenderSection) (Object) this;
        if (this.thorium$deferred) {
            // The section is already waiting for its turn; building it now would
            // defeat the deferral.
            ci.cancel();
            return;
        }

        BlockPos origin = section.getRenderOrigin();
        if (origin == null) {
            return;
        }
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        if (!ElytraFlightSmoother.shouldDefer(x, y, z)) {
            return;
        }

        boolean accepted = ThoriumChunkUploadPacer.deferSection(x, y, z, () -> {
            this.thorium$deferred = false;
            ThoriumChunkUploadPacer.runUndeferred(() -> section.rebuildSectionAsync(region));
        });
        if (accepted) {
            this.thorium$deferred = true;
            ci.cancel();
        }
    }
}
