package dev.hyperionfps.culling;

import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.memory.pool.BlockPosPool;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storage Room &amp; Item Frame Culling (REQ-HEAD-2).
 *
 * <p>A storage room is the worst case for a client renderer. Every chest is a
 * block-entity render, every shulker box is a block-entity render with an animated
 * lid, and every item frame is a <em>full entity render</em> - render-state
 * extraction, item stack, model, texture bind. Thorium's generic culler removes the
 * ones that are behind the camera, outside the frustum or far away, but it keeps a
 * lot of them: a chest is a full, bright, one-block object, and an item frame is
 * only half a block wide, so the generic 48/64 block distances are far too generous
 * for both.</p>
 *
 * <h2>The three decisions that are specific to storage</h2>
 * <ul>
 *   <li><b>Enclosed.</b> A container whose six face neighbours are all solid,
 *       opaque blocks that hold no block entity of their own cannot be seen from
 *       <em>any</em> direction - a chest buried in a wall, a chest in a sealed floor.
 *       Such a container is culled without a distance test, because the result is
 *       provably invisible: there is no pop-in and the picture does not change. The
 *       test is bounded - at most six block probes, and it short-circuits on the
 *       first neighbour that is not blocked.</li>
 *   <li><b>Distance.</b> A chest is a one-block object and an item frame is half a
 *       block wide, so both are gone from the screen long before the generic
 *       distances. The storage distances are deliberately tighter. An item frame past
 *       its distance is dropped outright; a container past its distance is only
 *       dropped when it is <em>also</em> off the view axis, so a chest the player is
 *       looking at is never popped out of the picture.</li>
 *   <li><b>Focal angle.</b> An item frame that is far off the axis the player is
 *       looking down is peripheral at best. Beyond a minimum distance the engine
 *       drops the ones outside the cone, which also skips the expensive per-entity
 *       work (cluster culling and the occlusion raycast) for everything that is
 *       nowhere near the view.</li>
 * </ul>
 *
 * <h2>What is never culled</h2>
 * <p>The container the player currently has open. It is identified through the open
 * container menu - the first slot of the menu points at the block entity that backs
 * it - so the check is exact rather than a radius around the player. A radius would
 * exempt every chest in a small storage room and defeat the whole feature. The
 * chest the player is <em>looking at</em> is protected by the same rules, because the
 * distance and focal-angle tests are both relative to the view.</p>
 *
 * <h2>Why the engine owns the whole decision</h2>
 * <p>{@link #shouldCullBlockEntity} and {@link #shouldCullEntity} are the <em>only</em>
 * entry points the culling mixins use. They fall back to {@link AsyncOcclusionCuller}
 * for everything they do not handle themselves, so switching this feature off
 * restores the previous behaviour exactly and switching it on only ever <em>adds</em>
 * a cull. That keeps the two concerns separable without duplicating the generic
 * policy in five per-version mixins (NFR-7).</p>
 *
 * <p>Every method here is a plain static: reading a flag never blocks and never
 * allocates (NFR-1, NFR-2). The block probes use a pooled mutable position, so the
 * enclosure test does not allocate either.</p>
 */
public final class StorageCullingEngine {

    /** Distance beyond which a storage container is a candidate for the cut. */
    public static final double CONTAINER_CULL_DISTANCE = 32.0;

    /** Distance beyond which a storage container is a candidate (Extreme mode). */
    public static final double CONTAINER_CULL_DISTANCE_EXTREME = 16.0;
    /** Distance beyond which an item frame is skipped entirely. */
    public static final double ITEM_FRAME_CULL_DISTANCE = 24.0;

    /** Distance beyond which an item frame is skipped entirely (Extreme mode). */
    public static final double ITEM_FRAME_CULL_DISTANCE_EXTREME = 12.0;

    /** Half angle of the cone the player is directly looking down. */
    public static final double FOCAL_CONE_HALF_ANGLE_DEGREES = 45.0;

    /** Cosine of {@link #FOCAL_CONE_HALF_ANGLE_DEGREES}; the pre-computed limit. */
    public static final double FOCAL_CONE_COSINE = 0.7071067811865476;

    /** Distance beyond which the focal-cone rule may drop an object. */
    public static final double FOCAL_CONE_MIN_DISTANCE = 16.0;

    /** Distance beyond which the focal-cone rule may drop an object (Extreme mode). */
    public static final double FOCAL_CONE_MIN_DISTANCE_EXTREME = 8.0;

    private static final AtomicLong CONTAINERS_CULLED = new AtomicLong();
    private static final AtomicLong ENCLOSED_CONTAINERS_CULLED = new AtomicLong();
    private static final AtomicLong ITEM_FRAMES_CULLED = new AtomicLong();
    private static final AtomicLong OPEN_CONTAINERS_EXEMPT = new AtomicLong();

    /**
     * The container menu that was inspected last.
     *
     * <p>The open container is looked up through the menu, and the menu instance only
     * changes when a container is opened or closed - so the lookup happens once per
     * opened container instead of once per rendered chest.</p>
     */
    private static volatile AbstractContainerMenu inspectedMenu;

    /** The storage container backing the inspected menu, if it is one. */
    private static volatile BlockEntity openContainerEntity;

    /** Yaw the cached view direction was computed for, in degrees. */
    private static volatile double cachedYaw = Double.NaN;

    /** Pitch the cached view direction was computed for, in degrees. */
    private static volatile double cachedPitch = Double.NaN;

    private static volatile double cachedDirX;
    private static volatile double cachedDirY;
    private static volatile double cachedDirZ;

    private StorageCullingEngine() {
    }

    // --------------------------------------------------------------------
    // Lifecycle
    // --------------------------------------------------------------------

    /**
     * Announces that the storage culler is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from "not
     * available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.STORAGE_ITEM_FRAMES.markWired();
    }

    /** Whether the player has the storage culler switched on. */
    public static boolean isEnabled() {
        return ThoriumFeatureToggles.isStorageItemFramesEnabled();
    }

    // --------------------------------------------------------------------
    // Block entities
    // --------------------------------------------------------------------

    /**
     * Decides whether a block entity may be skipped.
     *
     * <p>This is the single entry point for the block-entity culling mixins. It
     * handles the storage containers itself and delegates everything else to the
     * generic culler, so the generic rules keep working unchanged.</p>
     *
     * @param blockEntity the block entity that is about to be rendered
     * @param camX        camera X position
     * @param camY        camera Y position
     * @param camZ        camera Z position
     * @return whether the block entity may be skipped
     */
    public static boolean shouldCullBlockEntity(BlockEntity blockEntity,
                                                double camX, double camY, double camZ) {
        if (!isStorageContainer(blockEntity) || !isEnabled()) {
            return AsyncOcclusionCuller.shouldCullBlockEntity(blockEntity, camX, camY, camZ);
        }

        // The container the player is looking into is never skipped: its lid
        // animation and its contents are the whole point of the screen.
        if (isOpenContainer(blockEntity)) {
            OPEN_CONTAINERS_EXEMPT.incrementAndGet();
            return false;
        }

        long start = System.nanoTime();
        BlockPos pos = blockEntity.getBlockPos();
        double dx = pos.getX() + 0.5 - camX;
        double dy = pos.getY() + 0.5 - camY;
        double dz = pos.getZ() + 0.5 - camZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        boolean beyondDistance = isBeyondDistance(distanceSq, containerCullDistance());
        // The focal cone only matters once the container is far enough away to be
        // more than a few pixels wide - and the storage distance is already beyond
        // that, so it doubles as the proximity guard.
        boolean outsideFocalCone = beyondDistance && isOutsideFocalCone(dx, dy, dz);
        boolean enclosed = !beyondDistance && isEnclosed(blockEntity);
        dev.hyperionfps.telemetry.ThoriumPhaseTimer.addEntities(System.nanoTime() - start);

        if (decideContainerCull(false, enclosed, beyondDistance, outsideFocalCone)) {
            CONTAINERS_CULLED.incrementAndGet();
            if (enclosed) {
                ENCLOSED_CONTAINERS_CULLED.incrementAndGet();
            }
            return true;
        }
        return AsyncOcclusionCuller.shouldCullBlockEntity(blockEntity, camX, camY, camZ);
    }

    /**
     * Decides whether a block entity may be skipped, using the published camera.
     *
     * <p>Same decision as {@link #shouldCullBlockEntity(BlockEntity, double, double,
     * double)} with the camera of the current world frame.</p>
     */
    public static boolean shouldCullBlockEntity(BlockEntity blockEntity) {
        return shouldCullBlockEntity(blockEntity,
                AsyncOcclusionCuller.getCameraX(),
                AsyncOcclusionCuller.getCameraY(),
                AsyncOcclusionCuller.getCameraZ());
    }

    /**
     * The container policy, expressed without any game state.
     *
     * <p>Split out so it can be verified without a running Minecraft: the whole
     * decision table is four booleans.</p>
     *
     * @param openContainer    whether the player has this container open
     * @param enclosed         whether the container is sealed in opaque blocks
     * @param beyondDistance   whether the container is past the storage distance
     * @param outsideFocalCone whether the container is off the view axis
     * @return whether the container may be skipped
     */
    static boolean decideContainerCull(boolean openContainer, boolean enclosed,
                                       boolean beyondDistance, boolean outsideFocalCone) {
        if (openContainer) {
            // The container the player is looking into is never skipped.
            return false;
        }
        if (enclosed) {
            // Sealed in on all six sides: invisible from everywhere, no pop-in.
            return true;
        }
        // Far away <em>and</em> off to the side: too small on the screen to matter.
        // A container straight ahead is kept even at that distance.
        return beyondDistance && outsideFocalCone;
    }

    /**
     * Whether the given block entity is a storage container.
     *
     * <p>The chest types are listed explicitly even though the trapped chest is a
     * subclass of the plain chest on every supported Minecraft version: the explicit
     * list keeps working if a future version decouples them.</p>
     */
    public static boolean isStorageContainer(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        return blockEntity instanceof ChestBlockEntity
                || blockEntity instanceof TrappedChestBlockEntity
                || blockEntity instanceof EnderChestBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity;
    }

    /**
     * Whether the container cannot be seen from anywhere.
     *
     * <p>All six face neighbours must be solid, opaque blocks that hold no block
     * entity of their own. A container that is sealed in like that is invisible from
     * every angle, so dropping it cannot change a single pixel of the picture.</p>
     *
     * <p>The "no block entity of their own" clause is what keeps a double chest
     * intact: the neighbouring half is a chest, so the pair is never treated as
     * sealed in and one half can never vanish while the other one is on the screen.</p>
     *
     * <p>The test is bounded: at most six block probes, and it short-circuits on the
     * first neighbour that is not blocked - which is the common case, because a chest
     * in plain sight fails the very first probe (NFR-3).</p>
     */
    static boolean isEnclosed(BlockEntity blockEntity) {
        BlockGetter level = AsyncOcclusionCuller.getCurrentLevel();
        if (level == null) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        BlockPos.MutableBlockPos probe = BlockPosPool.get().acquire();
        return isBlocking(level, probe.set(x, y + 1, z))
                && isBlocking(level, probe.set(x, y - 1, z))
                && isBlocking(level, probe.set(x, y, z + 1))
                && isBlocking(level, probe.set(x, y, z - 1))
                && isBlocking(level, probe.set(x + 1, y, z))
                && isBlocking(level, probe.set(x - 1, y, z));
    }

    /**
     * Whether the block at the given position hides everything behind it.
     *
     * <p>Two independent conditions have to hold. The block must be opaque -
     * {@code canOcclude()} is false for glass, leaves and every other see-through
     * block. And it must be a full cube - {@code isCollisionShapeFullBlock} is false
     * for slabs, stairs, fences and for a chest, which occupies only 14/16 of its
     * cell. A chest behind a slab is still visible, so the slab must not count as a
     * blocker.</p>
     *
     * <p>Both methods have the same signature on all five supported Minecraft
     * versions, which is why they are used here instead of the era-dependent
     * {@code isSolidRender} (AGENTS.md §3).</p>
     */
    private static boolean isBlocking(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isBlockingNeighbour(state.canOcclude(),
                state.isCollisionShapeFullBlock(level, pos),
                level.getBlockEntity(pos) != null);
    }

    /**
     * Whether a neighbour hides the container behind it.
     *
     * <p>The pure form of the rule, so it can be verified without a running
     * Minecraft. A neighbour that carries a block entity of its own does not hide
     * anything: a chest next to a chest leaves a visible gap, and the neighbouring
     * half of a double chest must never disappear while its partner is on the
     * screen.</p>
     *
     * @param opaque        whether the block is opaque
     * @param fullCube      whether the block fills its whole cell
     * @param hasBlockEntity whether the block carries a block entity of its own
     * @return whether the neighbour blocks the view
     */
    static boolean isBlockingNeighbour(boolean opaque, boolean fullCube, boolean hasBlockEntity) {
        return opaque && fullCube && !hasBlockEntity;
    }

    /** The distance beyond which a container is a candidate in the current mode. */
    private static double containerCullDistance() {
        return AsyncOcclusionCuller.isExtremeFpsMode()
                ? CONTAINER_CULL_DISTANCE_EXTREME
                : CONTAINER_CULL_DISTANCE;
    }

    // --------------------------------------------------------------------
    // The open container
    // --------------------------------------------------------------------

    /**
     * Whether the given container is the one the player currently has open.
     *
     * <p>The open container is found through the player's container menu: the first
     * slot of the menu points at the container that is being shown, and for a chest
     * or a shulker box that container <em>is</em> the block entity. The lookup is
     * cached against the menu instance, so it happens once per opened container and
     * not once per rendered chest.</p>
     *
     * @param blockEntity the container to test
     * @return whether the container must be kept on the screen
     */
    public static boolean isOpenContainer(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        try {
            Minecraft client = Minecraft.getInstance();
            Player player = client == null ? null : client.player;
            if (player == null) {
                return false;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null || menu == player.inventoryMenu) {
                // No container is open.
                inspectedMenu = null;
                openContainerEntity = null;
                return false;
            }
            if (menu != inspectedMenu) {
                inspectedMenu = menu;
                openContainerEntity = resolveMenuContainer(menu);
            }
            if (blockEntity == openContainerEntity) {
                return true;
            }
            // The ender chest menu is backed by the player's ender inventory rather
            // than by the block entity, so the identity check cannot find it. Only
            // one ender chest can be open at a time, so the whole type is exempt.
            return blockEntity instanceof EnderChestBlockEntity;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Resolves the storage container that backs a container menu.
     *
     * <p>The first slot of the menu points at the container that is being shown, and
     * for a chest or a shulker box that container <em>is</em> the block entity. The
     * slot's container is compared as an {@link Object} so that the shared source
     * does not have to name the container interface, whose shape differs between the
     * Minecraft eras.</p>
     */
    private static BlockEntity resolveMenuContainer(AbstractContainerMenu menu) {
        try {
            if (menu.slots.isEmpty()) {
                return null;
            }
            Slot first = menu.slots.get(0);
            if (first == null) {
                return null;
            }
            Object container = first.container;
            return container instanceof BlockEntity blockEntity && isStorageContainer(blockEntity)
                    ? blockEntity
                    : null;
        } catch (Throwable unavailable) {
            return null;
        }
    }

    // --------------------------------------------------------------------
    // Entities
    // --------------------------------------------------------------------

    /**
     * Decides whether an entity may be skipped.
     *
     * <p>This is the single entry point for the entity culling mixins. It handles
     * the item frames itself and delegates everything else to the generic culler, so
     * the generic rules - including the occlusion raycast - keep working
     * unchanged.</p>
     *
     * @param entity the entity that is about to be rendered
     * @param camX   camera X position
     * @param camY   camera Y position
     * @param camZ   camera Z position
     * @return whether the entity may be skipped
     */
    public static boolean shouldCullEntity(Entity entity, double camX, double camY, double camZ) {
        if (!isItemFrame(entity) || !isEnabled()) {
            return AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ);
        }

        long start = System.nanoTime();
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        boolean beyondDistance = isBeyondDistance(distanceSq, itemFrameCullDistance());
        // The focal cone only matters once the frame is far enough away to be a
        // couple of pixels wide; closer frames are always drawn.
        boolean outsideFocalCone = !beyondDistance
                && isBeyondDistance(distanceSq, focalConeMinDistance())
                && isOutsideFocalCone(dx, dy, dz);
        dev.hyperionfps.telemetry.ThoriumPhaseTimer.addEntities(System.nanoTime() - start);

        if (decideItemFrameCull(beyondDistance, outsideFocalCone)) {
            ITEM_FRAMES_CULLED.incrementAndGet();
            return true;
        }
        return AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ);
    }

    /**
     * The item-frame policy, expressed without any game state.
     *
     * @param beyondDistance   whether the frame is past the item-frame distance
     * @param outsideFocalCone whether the frame is off the view axis
     * @return whether the frame may be skipped
     */
    static boolean decideItemFrameCull(boolean beyondDistance, boolean outsideFocalCone) {
        return beyondDistance || outsideFocalCone;
    }

    /**
     * Whether the given entity is an item frame.
     *
     * <p>The glowing item frame is a subclass of the plain one on every supported
     * Minecraft version, but it is listed explicitly for the same reason as the
     * chest types.</p>
     */
    public static boolean isItemFrame(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof ItemFrame || entity instanceof GlowItemFrame;
    }

    /** The distance beyond which an item frame is skipped in the current mode. */
    private static double itemFrameCullDistance() {
        return AsyncOcclusionCuller.isExtremeFpsMode()
                ? ITEM_FRAME_CULL_DISTANCE_EXTREME
                : ITEM_FRAME_CULL_DISTANCE;
    }

    /** The distance beyond which the focal-cone rule may drop an object. */
    private static double focalConeMinDistance() {
        return AsyncOcclusionCuller.isExtremeFpsMode()
                ? FOCAL_CONE_MIN_DISTANCE_EXTREME
                : FOCAL_CONE_MIN_DISTANCE;
    }

    // --------------------------------------------------------------------
    // Focal cone
    // --------------------------------------------------------------------

    /**
     * Whether the target lies outside the cone the player is directly looking down.
     *
     * <p>The cone is measured against the player's view direction. The camera looks
     * the same way in first person, and orbits around the player in third person, so
     * the player's rotation is the direction that decides what is on the screen in
     * both cases.</p>
     *
     * @param dx X offset from the camera to the target
     * @param dy Y offset from the camera to the target
     * @param dz Z offset from the camera to the target
     * @return whether the target is outside the cone
     */
    public static boolean isOutsideFocalCone(double dx, double dy, double dz) {
        try {
            Minecraft client = Minecraft.getInstance();
            Player player = client == null ? null : client.player;
            if (player == null) {
                return false;
            }
            return isOutsideFocalCone(dx, dy, dz, player.getYRot(), player.getXRot(), FOCAL_CONE_COSINE);
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Whether the target lies outside the cone the player is directly looking down.
     *
     * <p>The pure form of the test: it takes the view angles as arguments, so it can
     * be verified without a running game.</p>
     *
     * @param dx           X offset from the camera to the target
     * @param dy           Y offset from the camera to the target
     * @param dz           Z offset from the camera to the target
     * @param yawDegrees   the player's yaw, in degrees
     * @param pitchDegrees the player's pitch, in degrees
     * @param minCosine    cosine of the cone's half angle
     * @return whether the target is outside the cone
     */
    public static boolean isOutsideFocalCone(double dx, double dy, double dz,
                                              double yawDegrees, double pitchDegrees,
                                              double minCosine) {
        double lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq <= 1.0E-12) {
            // The target sits on the camera: it is straight ahead by definition.
            return false;
        }
        refreshViewDirection(yawDegrees, pitchDegrees);
        double cosine = (dx * cachedDirX + dy * cachedDirY + dz * cachedDirZ) / Math.sqrt(lengthSq);
        return cosine < minCosine;
    }

    /**
     * Recomputes the view direction when the player has turned.
     *
     * <p>The direction only changes when the yaw or the pitch changes, so the three
     * trigonometric calls are amortised over a whole frame instead of being paid
     * once per item frame.</p>
     */
    private static void refreshViewDirection(double yawDegrees, double pitchDegrees) {
        if (yawDegrees == cachedYaw && pitchDegrees == cachedPitch) {
            return;
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        cachedDirX = -Math.sin(yaw) * cosPitch;
        cachedDirY = -Math.sin(pitch);
        cachedDirZ = Math.cos(yaw) * cosPitch;
        cachedYaw = yawDegrees;
        cachedPitch = pitchDegrees;
    }

    // --------------------------------------------------------------------
    // Pure helpers
    // --------------------------------------------------------------------

    /**
     * Whether the target is farther away than the given limit.
     *
     * @param distanceSq the squared distance to the target
     * @param limit      the distance limit
     * @return whether the target is beyond the limit
     */
    public static boolean isBeyondDistance(double distanceSq, double limit) {
        return distanceSq > limit * limit;
    }

    // --------------------------------------------------------------------
    // Metrics
    // --------------------------------------------------------------------

    /** How many storage containers have been skipped. */
    public static long getContainersCulled() {
        return CONTAINERS_CULLED.get();
    }

    /** How many storage containers were skipped because they were sealed in. */
    public static long getEnclosedContainersCulled() {
        return ENCLOSED_CONTAINERS_CULLED.get();
    }

    /** How many item frames have been skipped. */
    public static long getItemFramesCulled() {
        return ITEM_FRAMES_CULLED.get();
    }

    /** How many containers were kept because the player has them open. */
    public static long getOpenContainersExempt() {
        return OPEN_CONTAINERS_EXEMPT.get();
    }

    /**
     * A one-line description of what the storage culler has done so far.
     *
     * <p>Used by the startup subsystem report so the log shows the real state of the
     * feature instead of a static claim (REQ-DIAG-1).</p>
     */
    public static String describeActivity() {
        return CONTAINERS_CULLED.get() + " containers, " + ITEM_FRAMES_CULLED.get() + " item frames culled";
    }

    /**
     * Resets the counters and the cached state.
     *
     * <p>Test seam: the engine is process-wide, so a test that culls a container has
     * to be able to put everything back.</p>
     */
    public static void resetMetrics() {
        CONTAINERS_CULLED.set(0L);
        ENCLOSED_CONTAINERS_CULLED.set(0L);
        ITEM_FRAMES_CULLED.set(0L);
        OPEN_CONTAINERS_EXEMPT.set(0L);
        inspectedMenu = null;
        openContainerEntity = null;
        cachedYaw = Double.NaN;
        cachedPitch = Double.NaN;
    }
}
