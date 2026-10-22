package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.input.ThoriumReflexController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/**
 * The late input sample (REQ-HEAD-7).
 *
 * <p>{@code MouseHandler#handleAccumulatedMovement()} is the point at which the game
 * applies the collected cursor movement to the camera, and it exists with the
 * identical descriptor {@code ()V} on every supported Minecraft version. On all five
 * nodes the game calls it immediately before it builds the camera matrix:</p>
 *
 * <pre>
 *   1.21.x :  runTick &#8594; tick &#8594; handleAccumulatedMovement &#8594; render
 *   26.x   :  runTick &#8594; tick &#8594; handleAccumulatedMovement &#8594; renderFrame
 * </pre>
 *
 * <p>That makes it the earliest point at which the freshest possible input is still
 * useful: everything the hook drains here is applied to the frame the player is
 * looking at, instead of the next one.</p>
 *
 * <p>The hook drains the platform event queue through the version-specific
 * {@link dev.hyperionfps.input.ReflexEventPump} and measures what it recovered. The
 * decision itself lives in {@link ThoriumReflexController}, so there is exactly one
 * copy of it.</p>
 */
@Mixin(MouseHandler.class)
public abstract class ReflexInputMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), require = 0)
    private void hyperionfps$lateInputSample(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !ThoriumReflexController.shouldSample(client.isWindowActive())) {
            return;
        }

        MouseHandlerAccessor accessor = (MouseHandlerAccessor) (Object) this;
        double beforeX = accessor.thorium$getAccumulatedDX();
        double beforeY = accessor.thorium$getAccumulatedDY();

        if (!ThoriumReflexController.pumpEvents()) {
            return;
        }

        // Whatever the drain added is movement that arrived while this frame was
        // working. Vanilla would have applied it one frame later.
        double afterX = accessor.thorium$getAccumulatedDX();
        double afterY = accessor.thorium$getAccumulatedDY();
        ThoriumReflexController.recordLateSample(afterX - beforeX, afterY - beforeY);
    }
}
