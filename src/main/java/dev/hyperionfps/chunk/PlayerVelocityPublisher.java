package dev.hyperionfps.chunk;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the player's velocity vector and publishes it to the
 * {@link ElytraFlightSmoother}.
 *
 * <p>This is the only place in the codebase that touches the player entity, and it
 * exists as a separate class on purpose: the policy engine
 * ({@link ElytraFlightSmoother}) and the pacer ({@link ThoriumChunkUploadPacer}) hold
 * no Minecraft type at all, so the whole decision table stays unit-testable without a
 * running game. This reader is loaded by the era hooks only.</p>
 *
 * <p>{@code Minecraft#player} is a public field and
 * {@code Entity#getDeltaMovement()} exists with the same name and the same return
 * type on all five supported versions, so the reader is shared rather than
 * era-specific.</p>
 */
public final class PlayerVelocityPublisher {

    private PlayerVelocityPublisher() {
    }

    /**
     * Publishes the player's current velocity.
     *
     * <p>Outside a world - or in a menu - the velocity is cleared, so the smoother
     * never acts on a stale vector.</p>
     */
    public static void publish() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) {
                ElytraFlightSmoother.clearVelocity();
                return;
            }
            Vec3 movement = client.player.getDeltaMovement();
            if (movement == null) {
                ElytraFlightSmoother.clearVelocity();
                return;
            }
            ElytraFlightSmoother.publishVelocity(movement.x, movement.y, movement.z);
        } catch (Throwable failure) {
            // A player that cannot be read must not take the frame down with it; the
            // smoother simply does not act this frame.
            ElytraFlightSmoother.clearVelocity();
        }
    }
}
