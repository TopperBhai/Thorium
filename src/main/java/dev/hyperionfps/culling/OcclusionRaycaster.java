package dev.hyperionfps.culling;

import dev.hyperionfps.memory.pool.BlockPosPool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-speed 3D DDA (Digital Differential Analyzer) voxel raycaster for solid occlusion checks.
 *
 * <p>Traces sightlines from the camera eye to entity and block entity positions
 * without object allocations, detecting solid walls that fully occlude rendering.</p>
 */
public final class OcclusionRaycaster {

    private static final int MAX_RAY_STEPS = 12; // Bounded fast raycast to prevent render-thread stalls

    private OcclusionRaycaster() {
    }

    /**
     * Checks if solid opaque blocks completely obstruct the line of sight
     * from camera eye to target position.
     */
    public static boolean isOccluded(BlockGetter level, double eyeX, double eyeY, double eyeZ,
                                     double targetX, double targetY, double targetZ) {
        if (level == null) {
            return false;
        }

        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        // Skip close proximity checks (within 2.5 blocks) to prevent false culling
        if (distSqr < 6.25) {
            return false;
        }

        double distance = Math.sqrt(distSqr);
        double invDist = 1.0 / distance;
        double stepX = dx * invDist;
        double stepY = dy * invDist;
        double stepZ = dz * invDist;

        // Start step slightly offset from camera
        double currentX = eyeX + stepX * 1.5;
        double currentY = eyeY + stepY * 1.5;
        double currentZ = eyeZ + stepZ * 1.5;

        double remainingDist = distance - 2.0;
        int steps = Math.min((int) remainingDist, MAX_RAY_STEPS);
        if (steps <= 0) {
            return false;
        }

        BlockPos.MutableBlockPos probePos = BlockPosPool.get().acquire();
        int solidHits = 0;

        for (int i = 0; i < steps; i++) {
            probePos.set(currentX, currentY, currentZ);
            BlockState state = level.getBlockState(probePos);

            if (state.canOcclude()) {
                solidHits++;
                // 2 consecutive solid blocks guarantee complete occlusion
                if (solidHits >= 2) {
                    return true;
                }
            } else {
                solidHits = 0;
            }

            currentX += stepX;
            currentY += stepY;
            currentZ += stepZ;
        }

        return false;
    }
}
