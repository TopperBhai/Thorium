package dev.hyperionfps.mixin.modern;

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
 * <p>26.1 is the one modern node that still schedules a section build through
 * {@code SectionRenderDispatcher$RenderSection#rebuildSectionAsync}; 26.2 and 26.3
 * renamed it to {@code compileAsync} and changed the region type. The shape of the
 * call is identical on all three - it creates the compile task and hands it to the
 * dispatcher - so the deferral works the same way.</p>
 *
 * <p>While the player is flying fast, a section that lies behind the flight vector is
 * held back by {@link ThoriumChunkUploadPacer} instead of being built right away. The
 * pacer releases it later, in the order the flight vector demands, so the world ahead
 * of the player is built first - and because fewer sections are built per frame, the
 * 26.x upload burst shrinks as well.</p>
 *
 * <p>The method is a pure scheduling call - it creates a task and queues it - so
 * calling it a few frames later is safe. The pacer additionally releases anything
 * that has waited for more than half a second, so a deferral can never turn into a
 * permanent hole in the world.</p>
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class ModernChunkUploadPriorityMixin {

    /**
     * Whether this section is already waiting in Thorium's deferred queue.
     *
     * <p>Vanilla may ask for the same section again while it waits. The flag keeps
     * one section from being queued twice.</p>
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
