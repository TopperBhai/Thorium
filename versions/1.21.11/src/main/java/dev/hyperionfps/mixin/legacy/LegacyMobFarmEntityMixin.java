package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.anim.MobFarmOptimizer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Records the living entities of a frame in the mob farm cluster map (1.21.11).
 *
 * <p>The render pipeline of the modern era extracts an {@code EntityRenderState}
 * from the entity and renders the state, so the animation hook never sees the
 * entity itself. This hook runs during that extraction, which is the last point at
 * which the leash and the passenger flags are still available - and the cluster
 * map needs them, because an exempt mob must neither be counted nor throttled.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyMobFarmEntityMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"), require = 0)
    private void thorium$observeMobCluster(Entity entity, float partialTick,
                                           CallbackInfoReturnable<EntityRenderState> cir) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        MobFarmOptimizer.observe(entity.getType(), entity.getX(), entity.getZ(),
                MobFarmOptimizer.isExemptEntity(entity));
    }
}
