package dev.hyperionfps.anim;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;

/**
 * Mob Farm &amp; Swarm Optimizer (REQ-HEAD-1).
 *
 * <p>A mob farm is the worst case for a client renderer. Thirty zombies in a pen
 * are thirty skeletal animation updates per frame: every one of them walks the bone
 * hierarchy, computes the limb swing and rebuilds the model matrices. The player
 * cannot make the farm stop existing, but the client does not have to pay the full
 * price for a mob it is not looking at.</p>
 *
 * <h2>What the optimizer does</h2>
 * <ol>
 *   <li><b>Cluster detection.</b> Living entities are grouped into a grid of
 *       {@value #CLUSTER_CELL_SIZE} x {@value #CLUSTER_CELL_SIZE} block cells. A cell
 *       that holds more than {@value #CLUSTER_THRESHOLD} living entities of the
 *       <em>same</em> species is a mob farm. The grid is the classic spatial hash: the
 *       cost of a lookup is one hash and one probe, and the whole map is rebuilt from
 *       scratch every frame, so it can never go stale.</li>
 *   <li><b>Focus.</b> A mob within {@value #FOCUS_DISTANCE} blocks of the camera is
 *       never throttled - that is the mob the player is looking at.</li>
 *   <li><b>Throttling.</b> The animation of a clustered mob is recomputed on one frame
 *       out of {@value #ANIMATION_DIVISOR}. The two skipped frames perform no bone and
 *       no limb-swing calculation at all; the model keeps the pose it had.</li>
 * </ol>
 *
 * <h2>What is never throttled</h2>
 * <p>The local player, a mob with a custom name, a leashed mob and a mob that is
 * riding something are all exempt. An exempt mob is not counted towards the cluster
 * <em>and</em> it protects its whole cell from being throttled, because a client that
 * freezes the legs of the pet the player is looking at is worse than a client that
 * does not save the work. That rule is deliberately conservative: it can only ever
 * cost performance, never correctness.</p>
 *
 * <h2>Why the map is one frame behind</h2>
 * <p>The map is filled while the entities are rendered and read while they are
 * rendered, so a decision can only use the map of the <em>previous</em> frame. That is
 * not a compromise - it is the same one-frame lag every spatial tracker uses - and it
 * costs nothing: a farm that is dense now was dense a frame ago as well.</p>
 *
 * <h2>Allocation</h2>
 * <p>Nothing on this path allocates (NFR-1). The map is a pair of open-addressed
 * primitive tables that are cleared and refilled every frame, and every decision is
 * a handful of arithmetic operations on a published camera position.</p>
 */
public final class MobFarmOptimizer {

    /** Edge length of a cluster cell, in blocks: a 5x5 horizontal area. */
    public static final int CLUSTER_CELL_SIZE = 5;

    /** A cell needs <em>more</em> than this many living entities to be a cluster. */
    public static final int CLUSTER_THRESHOLD = 15;

    /** Mobs closer than this to the camera are never throttled, in blocks. */
    public static final double FOCUS_DISTANCE = 4.0;

    /** A throttled mob is animated on one frame out of this many. */
    public static final int ANIMATION_DIVISOR = 3;

    private static final AtomicLong CLUSTERS_DETECTED = new AtomicLong();
    private static final AtomicLong ANIMATIONS_SKIPPED = new AtomicLong();
    private static final AtomicLong THROTTLED_MOBS = new AtomicLong();

    /** The map of the last completed frame. Read by every decision. */
    private static volatile ClusterIndex active = new ClusterIndex();

    /** The map that is being filled during the current frame. */
    private static volatile ClusterIndex building = new ClusterIndex();

    /** The frame counter that drives the rate limit. */
    private static final AtomicLong FRAME_INDEX = new AtomicLong();

    private MobFarmOptimizer() {
    }

    // --------------------------------------------------------------------
    // Lifecycle
    // --------------------------------------------------------------------

    /**
     * Announces that the mob farm optimizer is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from "not
     * available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.MOB_FARM.markWired();
    }

    /** Whether the player has the mob farm optimizer switched on. */
    public static boolean isEnabled() {
        return ThoriumFeatureToggles.isMobFarmEnabled();
    }

    /**
     * Opens a new frame and publishes the map of the frame that just ended.
     *
     * <p>Called once per rendered world frame from the frame context. The map that
     * was filled during the previous frame becomes the authoritative one, and the
     * map of the frame before that is recycled as the new building map.</p>
     */
    public static void beginFrame() {
        ClusterIndex completed = building;
        ClusterIndex recycled = active;
        recycled.clear();
        building = recycled;
        active = completed;
        FRAME_INDEX.incrementAndGet();
    }

    // --------------------------------------------------------------------
    // Observation
    // --------------------------------------------------------------------

    /**
     * Records a living entity in the cluster map of the current frame.
     *
     * <p>An exempt entity is not counted - a named mob must not push a pen over the
     * threshold - but it marks its cell so that the cell is never throttled.</p>
     *
     * @param species the species of the entity; the {@code EntityType} instance
     * @param x       the entity's world X position
     * @param z       the entity's world Z position
     * @param exempt  whether the entity is exempt from throttling
     */
    public static void observe(Object species, double x, double z, boolean exempt) {
        if (!isEnabled() || species == null) {
            return;
        }
        int cellX = cellCoordinate(x);
        int cellZ = cellCoordinate(z);
        ClusterIndex map = building;
        boolean becameDense = map.observe(species, cellX, cellZ, exempt);
        if (becameDense && !map.isDenseIn(active, species, cellX, cellZ)) {
            CLUSTERS_DETECTED.incrementAndGet();
        }
    }

    // --------------------------------------------------------------------
    // The exemption rule
    // --------------------------------------------------------------------

    /**
     * Whether an entity is exempt from the mob farm throttling.
     *
     * <p>The pure form of the rule, so it can be verified without a running game. The
     * facts are gathered by the era-specific render hooks - the modern render state
     * does not carry the leash and the passenger flags, so they are collected where
     * the entity itself is still available.</p>
     *
     * @param isPlayer  whether the entity is the local player
     * @param named     whether the entity carries a custom name
     * @param leashed   whether the entity is on a leash
     * @param passenger whether the entity is riding something
     * @return whether the entity must never be throttled
     */
    public static boolean isExemptEntity(boolean isPlayer, boolean named, boolean leashed, boolean passenger) {
        return isPlayer || named || leashed || passenger;
    }

    /**
     * Whether an entity is exempt from the mob farm throttling.
     *
     * <p>The version that reads the facts off the entity itself. The leash flag is
     * read defensively: a mob that is being leashed at the very moment it is
     * rendered may have no leash data yet, and a rendering client must never fail
     * because of that.</p>
     *
     * @param entity the entity to test
     * @return whether the entity must never be throttled
     */
    public static boolean isExemptEntity(Entity entity) {
        if (entity == null) {
            return true;
        }
        boolean leashed = false;
        try {
            leashed = entity instanceof Leashable leashable && leashable.isLeashed();
        } catch (Throwable unavailable) {
            leashed = false;
        }
        return isExemptEntity(entity instanceof Player,
                entity.hasCustomName(),
                leashed,
                entity.isPassenger());
    }

    // --------------------------------------------------------------------
    // The decision
    // --------------------------------------------------------------------

    /**
     * Whether a mob's animation may be skipped this frame.
     *
     * <p>This is the single entry point the render hooks use. It applies the cluster
     * rule and then falls back to the distance based animation LOD, so switching the
     * mob farm optimizer off restores the previous behaviour exactly.</p>
     *
     * @param species    the species of the entity
     * @param x          the entity's world X position
     * @param y          the entity's world Y position
     * @param z          the entity's world Z position
     * @param distanceSq the squared distance to the camera
     * @param entityHash a stable per-entity hash, used to spread the skipped frames
     * @param isSpecial  whether the entity is a named or otherwise protected entity
     * @return whether the animation may be skipped
     */
    public static boolean shouldSkipAnimation(Object species, double x, double y, double z,
                                               double distanceSq, int entityHash, boolean isSpecial) {
        if (shouldThrottle(species, x, y, z, entityHash)) {
            return true;
        }
        return ThoriumEntityLodThrottler.shouldSkipAnimation(distanceSq, entityHash, isSpecial);
    }

    /**
     * The cluster rule, expressed without any game state.
     *
     * <p>The pure form of the decision, split out so it can be verified without a
     * running Minecraft.</p>
     *
     * @param dense       whether the entity's cell holds a dense cluster of its species
     * @param cellExempt  whether the cell contains an exempt entity of that species
     * @param withinFocus whether the entity is close enough to the camera to be watched
     * @param rateLimited whether this frame is one of the frames that are skipped
     * @return whether the animation may be skipped
     */
    static boolean decideThrottle(boolean dense, boolean cellExempt, boolean withinFocus, boolean rateLimited) {
        if (!dense || cellExempt || withinFocus) {
            return false;
        }
        return rateLimited;
    }

    /**
     * Whether the cluster rule throttles the given entity.
     *
     * <p>The live path: it reads the published cluster map and the published camera
     * and answers with the pure {@link #decideThrottle} rule.</p>
     *
     * @param species    the species of the entity
     * @param x          the entity's world X position
     * @param y          the entity's world Y position
     * @param z          the entity's world Z position
     * @param entityHash a stable per-entity hash
     * @return whether the entity's animation is throttled this frame
     */
    public static boolean shouldThrottle(Object species, double x, double y, double z, int entityHash) {
        if (!isEnabled() || species == null) {
            return false;
        }
        int cellX = cellCoordinate(x);
        int cellZ = cellCoordinate(z);
        ClusterIndex map = active;
        boolean dense = isDenseCluster(map.count(species, cellX, cellZ));
        boolean cellExempt = map.hasExempt(species, cellX, cellZ);
        boolean withinFocus = distanceSqToCamera(x, y, z) <= FOCUS_DISTANCE * FOCUS_DISTANCE;
        boolean rateLimited = !isAnimationFrame(entityHash);
        if (decideThrottle(dense, cellExempt, withinFocus, rateLimited)) {
            ANIMATIONS_SKIPPED.incrementAndGet();
            THROTTLED_MOBS.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Whether a cell holds a dense cluster of one species.
     *
     * <p>The pure form of the threshold rule.</p>
     *
     * @param sameSpeciesCount how many living entities of one species the cell holds
     * @return whether the cell is a mob farm
     */
    public static boolean isDenseCluster(int sameSpeciesCount) {
        return sameSpeciesCount > CLUSTER_THRESHOLD;
    }

    /**
     * Whether this frame is one of the frames on which the animation is recomputed.
     *
     * <p>A throttled mob animates on one frame out of {@value #ANIMATION_DIVISOR}.
     * The per-entity hash spreads the skipped frames over the mob, so a pen of thirty
     * mobs does not freeze in unison - that would read as a stutter instead of as
     * throttling.</p>
     *
     * @param entityHash a stable per-entity hash
     * @return whether the animation should be recomputed this frame
     */
    static boolean isAnimationFrame(int entityHash) {
        long frame = FRAME_INDEX.get();
        return ((frame + entityHash) % ANIMATION_DIVISOR) == 0L;
    }

    /**
     * The squared distance between a position and the published camera.
     *
     * @param x the world X position
     * @param y the world Y position
     * @param z the world Z position
     * @return the squared distance
     */
    public static double distanceSqToCamera(double x, double y, double z) {
        double dx = x - AsyncOcclusionCuller.getCameraX();
        double dy = y - AsyncOcclusionCuller.getCameraY();
        double dz = z - AsyncOcclusionCuller.getCameraZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The grid coordinate of a world position.
     *
     * @param value the world coordinate
     * @return the cell coordinate
     */
    public static int cellCoordinate(double value) {
        return (int) Math.floor(value / CLUSTER_CELL_SIZE);
    }

    // --------------------------------------------------------------------
    // The sound governor's view of the map
    // --------------------------------------------------------------------

    /**
     * Whether a position sits in a cell that holds a mob farm.
     *
     * <p>The sound governor uses this to dampen the ambience of a mob farm: a cell
     * is dense when <em>some</em> species in it exceeds the threshold, which is the
     * same rule the animation uses.</p>
     *
     * @param x the world X position
     * @param z the world Z position
     * @return whether the cell holds a mob farm
     */
    public static boolean isDenseCell(double x, double z) {
        if (!isEnabled()) {
            return false;
        }
        return active.isDenseCell(cellCoordinate(x), cellCoordinate(z));
    }

    // --------------------------------------------------------------------
    // Metrics
    // --------------------------------------------------------------------

    /** How many distinct mob farm clusters have been detected. */
    public static long getClustersDetected() {
        return CLUSTERS_DETECTED.get();
    }

    /** How many mob animations have been skipped. */
    public static long getAnimationsSkipped() {
        return ANIMATIONS_SKIPPED.get();
    }

    /** How many mob animation frames have been throttled. */
    public static long getThrottledMobs() {
        return THROTTLED_MOBS.get();
    }

    /**
     * A one-line description of what the optimizer has done so far.
     *
     * <p>Used by the startup subsystem report so the log shows the real state of the
     * feature instead of a static claim (REQ-DIAG-1).</p>
     */
    public static String describeActivity() {
        return CLUSTERS_DETECTED.get() + " clusters, "
                + ANIMATIONS_SKIPPED.get() + " animations skipped";
    }

    /**
     * Resets the counters and the maps.
     *
     * <p>Test seam: the optimizer is process-wide, so a test that throttles a mob has
     * to be able to put everything back.</p>
     */
    public static void resetMetrics() {
        CLUSTERS_DETECTED.set(0L);
        ANIMATIONS_SKIPPED.set(0L);
        THROTTLED_MOBS.set(0L);
        active.clear();
        building.clear();
        FRAME_INDEX.set(0L);
    }

    // --------------------------------------------------------------------
    // The map
    // --------------------------------------------------------------------

    /**
     * The cluster map of one frame.
     *
     * <p>Two open-addressed tables, both keyed by a hash and both verified against
     * the stored reference, so a hash collision between two different species can
     * never merge two clusters. The tables are cleared and refilled every frame, so
     * they never grow and never need a sweep.</p>
     */
    private static final class ClusterIndex {

        /** Number of slots; a power of two so the mask is a cheap modulo. */
        private static final int CAPACITY = 1024;

        /** The mask that turns a hash into a slot. */
        private static final int MASK = CAPACITY - 1;

        /** The cluster key of each slot. */
        private final long[] keys = new long[CAPACITY];

        /** The species each slot belongs to, used to resolve hash collisions. */
        private final Object[] species = new Object[CAPACITY];

        /** How many living entities of that species the slot's cell holds. */
        private final int[] counts = new int[CAPACITY];

        /** Whether the slot's cell holds an exempt entity of that species. */
        private final boolean[] exempt = new boolean[CAPACITY];

        /** The cell key of each slot of the cell table. */
        private final long[] cellKeys = new long[CAPACITY];

        /** Whether a slot of the cell table is in use. */
        private final boolean[] cellUsed = new boolean[CAPACITY];

        /** How many slots of the species table are in use. */
        private int size;

        /** Empties both tables. */
        void clear() {
            Arrays.fill(species, null);
            Arrays.fill(cellUsed, false);
            size = 0;
        }

        /**
         * Records one living entity.
         *
         * @param speciesKey the species of the entity
         * @param cellX      the cell X coordinate
         * @param cellZ      the cell Z coordinate
         * @param isExempt   whether the entity is exempt from throttling
         * @return whether the cluster of that species just became dense
         */
        boolean observe(Object speciesKey, int cellX, int cellZ, boolean isExempt) {
            int slot = findSlot(speciesKey, cellX, cellZ);
            if (slot < 0) {
                slot = freeSlot(speciesKey, cellX, cellZ);
                if (slot < 0) {
                    // The table is full; the cluster is simply not tracked this frame.
                    return false;
                }
            }
            int before = counts[slot];
            if (isExempt) {
                exempt[slot] = true;
            } else {
                counts[slot]++;
            }
            boolean dense = isDenseCluster(counts[slot]);
            if (dense) {
                markCellDense(cellX, cellZ);
            }
            return dense && !isDenseCluster(before);
        }

        /**
         * Whether a cluster was already dense in another map.
         *
         * @param other      the map to compare with
         * @param speciesKey the species of the entity
         * @param cellX      the cell X coordinate
         * @param cellZ      the cell Z coordinate
         * @return whether the cluster was dense in that map
         */
        boolean isDenseIn(ClusterIndex other, Object speciesKey, int cellX, int cellZ) {
            return isDenseCluster(other.count(speciesKey, cellX, cellZ));
        }

        /** How many living entities of a species a cell holds. */
        int count(Object speciesKey, int cellX, int cellZ) {
            int slot = findSlot(speciesKey, cellX, cellZ);
            return slot < 0 ? 0 : counts[slot];
        }

        /** Whether a cell holds an exempt entity of a species. */
        boolean hasExempt(Object speciesKey, int cellX, int cellZ) {
            int slot = findSlot(speciesKey, cellX, cellZ);
            return slot >= 0 && exempt[slot];
        }

        /**
         * Whether a cell holds a dense cluster of some species.
         *
         * <p>The cell table only holds the cells that <em>are</em> dense, so a lookup
         * that finds nothing answers the question directly.</p>
         */
        boolean isDenseCell(int cellX, int cellZ) {
            long key = cellKey(cellX, cellZ);
            int slot = (int) (mix(key) & MASK);
            for (int probe = 0; probe < CAPACITY; probe++) {
                if (!cellUsed[slot]) {
                    return false;
                }
                if (cellKeys[slot] == key) {
                    return true;
                }
                slot = (slot + 1) & MASK;
            }
            return false;
        }

        /** Flags a cell as holding a dense cluster. */
        private void markCellDense(int cellX, int cellZ) {
            long key = cellKey(cellX, cellZ);
            int slot = (int) (mix(key) & MASK);
            for (int probe = 0; probe < CAPACITY; probe++) {
                if (!cellUsed[slot]) {
                    cellUsed[slot] = true;
                    cellKeys[slot] = key;
                    return;
                }
                if (cellKeys[slot] == key) {
                    return;
                }
                slot = (slot + 1) & MASK;
            }
        }

        /**
         * Finds the slot of a species in a cell.
         *
         * @return the slot, or {@code -1} when the cluster is not tracked
         */
        private int findSlot(Object speciesKey, int cellX, int cellZ) {
            long key = clusterKey(speciesKey, cellX, cellZ);
            int slot = (int) (mix(key) & MASK);
            for (int probe = 0; probe < CAPACITY; probe++) {
                Object stored = species[slot];
                if (stored == null) {
                    return -1;
                }
                if (keys[slot] == key && stored == speciesKey) {
                    return slot;
                }
                slot = (slot + 1) & MASK;
            }
            return -1;
        }

        /**
         * Claims a free slot for a species in a cell.
         *
         * @return the slot, or {@code -1} when the table is full
         */
        private int freeSlot(Object speciesKey, int cellX, int cellZ) {
            if (size >= CAPACITY / 2) {
                return -1;
            }
            long key = clusterKey(speciesKey, cellX, cellZ);
            int slot = (int) (mix(key) & MASK);
            for (int probe = 0; probe < CAPACITY; probe++) {
                if (species[slot] == null) {
                    species[slot] = speciesKey;
                    keys[slot] = key;
                    counts[slot] = 0;
                    exempt[slot] = false;
                    size++;
                    return slot;
                }
                slot = (slot + 1) & MASK;
            }
            return -1;
        }

        /**
         * The key of a species in a cell.
         *
         * <p>The species is identified by its identity, so the same {@code EntityType}
         * instance always produces the same key - and two different species never
         * share a slot, because the stored reference is compared as well.</p>
         */
        private static long clusterKey(Object speciesKey, int cellX, int cellZ) {
            long cell = cellKey(cellX, cellZ);
            long species = System.identityHashCode(speciesKey) & 0xFFFFFFFFL;
            return mix(cell * 0x9E3779B97F4A7C15L ^ species);
        }

        /** The key of a cell. */
        private static long cellKey(int cellX, int cellZ) {
            return ((long) (cellX & 0xFFFFF) << 20) | (cellZ & 0xFFFFF);
        }

        /** The splitmix64 finalizer: a cheap, well distributed mixer. */
        private static long mix(long value) {
            long z = value;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }
}
