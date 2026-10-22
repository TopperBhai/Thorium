package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.MouseHandler;

/**
 * Reads the mouse movement the game has accumulated but not yet applied.
 *
 * <p>{@code MouseHandler} collects the cursor movement in {@code accumulatedDX} and
 * {@code accumulatedDY} and turns it into a camera rotation in
 * {@code handleAccumulatedMovement()}, where both fields are reset to zero. The two
 * fields carry the same name and the same type on every supported Minecraft version,
 * which is why a single accessor covers the whole matrix - and why the build-time
 * mixin audit proves on every node that the fields are really there.</p>
 *
 * <p>The late input sampler uses the accessor to measure what its extra drain
 * recovered: the difference between the pending movement before and after the drain
 * is exactly the movement that vanilla would have applied one frame later.</p>
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    /** The horizontal cursor movement the game has collected but not yet applied. */
    @Accessor("accumulatedDX")
    double thorium$getAccumulatedDX();

    /** The vertical cursor movement the game has collected but not yet applied. */
    @Accessor("accumulatedDY")
    double thorium$getAccumulatedDY();
}
