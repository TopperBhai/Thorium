package dev.hyperionfps.memory.pool;

import net.minecraft.world.phys.Vec3;

/**
 * High-performance, zero-allocation mutable 3D vector for camera, raycast, and frustum calculations.
 */
public final class PooledVec3 {

    public double x;
    public double y;
    public double z;

    public PooledVec3() {
        this(0, 0, 0);
    }

    public PooledVec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PooledVec3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public PooledVec3 set(Vec3 vec) {
        return set(vec.x, vec.y, vec.z);
    }

    public PooledVec3 add(double dx, double dy, double dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    public PooledVec3 subtract(double dx, double dy, double dz) {
        this.x -= dx;
        this.y -= dy;
        this.z -= dz;
        return this;
    }

    public double distanceToSqr(double targetX, double targetY, double targetZ) {
        double dx = this.x - targetX;
        double dy = this.y - targetY;
        double dz = this.z - targetZ;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(double targetX, double targetY, double targetZ) {
        return Math.sqrt(distanceToSqr(targetX, targetY, targetZ));
    }

    public double dot(double otherX, double otherY, double otherZ) {
        return this.x * otherX + this.y * otherY + this.z * otherZ;
    }

    public PooledVec3 normalize() {
        double lenSqr = x * x + y * y + z * z;
        if (lenSqr > 1.0E-8) {
            double invLen = 1.0 / Math.sqrt(lenSqr);
            this.x *= invLen;
            this.y *= invLen;
            this.z *= invLen;
        }
        return this;
    }

    /** Instantiates an immutable vanilla Vec3 only when strictly necessary for external APIs. */
    public Vec3 toVanilla() {
        return new Vec3(this.x, this.y, this.z);
    }
}
