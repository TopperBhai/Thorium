package dev.hyperionfps.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import dev.hyperionfps.memory.pool.BlockPosPool;
import dev.hyperionfps.memory.pool.MatrixPool;
import dev.hyperionfps.memory.pool.PooledMatrix4f;
import dev.hyperionfps.memory.pool.PooledQuaternion;
import dev.hyperionfps.memory.pool.PooledVec3;
import dev.hyperionfps.memory.pool.Vec3Pool;
import dev.hyperionfps.memory.pool.ZeroAllocationEngine;
import net.minecraft.core.BlockPos;

class ZeroAllocationPoolTest {

    @Test
    void blockPosPoolServesValidMutableBlockPos() {
        BlockPos.MutableBlockPos pos1 = BlockPosPool.get().acquire(10, 20, 30);
        assertNotNull(pos1);
        assertEquals(10, pos1.getX());
        assertEquals(20, pos1.getY());
        assertEquals(30, pos1.getZ());

        // In Minecraft, floor(-200.5) is -201
        BlockPos.MutableBlockPos pos2 = ZeroAllocationEngine.pooledBlockPos(100.5, 64.0, -200.5);
        assertEquals(100, pos2.getX());
        assertEquals(64, pos2.getY());
        assertEquals(-201, pos2.getZ());
    }

    @Test
    void vec3PoolMathAndNormalization() {
        PooledVec3 v1 = Vec3Pool.get().acquire(3.0, 4.0, 0.0);
        assertEquals(25.0, v1.distanceToSqr(0, 0, 0), 0.0001);
        assertEquals(5.0, v1.distanceTo(0, 0, 0), 0.0001);

        v1.normalize();
        assertEquals(1.0, v1.distanceTo(0, 0, 0), 0.0001);
        assertEquals(0.6, v1.x, 0.0001);
        assertEquals(0.8, v1.y, 0.0001);

        PooledVec3 v2 = ZeroAllocationEngine.pooledVec3(1.0, 2.0, 3.0);
        v2.add(2.0, 3.0, 4.0);
        assertEquals(3.0, v2.x, 0.0001);
        assertEquals(5.0, v2.y, 0.0001);
        assertEquals(7.0, v2.z, 0.0001);
    }

    @Test
    void matrixPoolTransforms() {
        PooledMatrix4f mat = MatrixPool.get().acquireMatrix();
        assertNotNull(mat);
        assertEquals(1.0f, mat.m[0]);
        assertEquals(1.0f, mat.m[5]);
        assertEquals(1.0f, mat.m[10]);
        assertEquals(1.0f, mat.m[15]);

        mat.translate(5.0f, 10.0f, 15.0f);
        assertEquals(5.0f, mat.m[12]);
        assertEquals(10.0f, mat.m[13]);
        assertEquals(15.0f, mat.m[14]);

        PooledQuaternion quat = ZeroAllocationEngine.pooledQuaternion();
        assertNotNull(quat);
        assertEquals(0.0f, quat.x);
        assertEquals(0.0f, quat.y);
        assertEquals(0.0f, quat.z);
        assertEquals(1.0f, quat.w);
    }
}
