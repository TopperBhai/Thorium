package dev.hyperionfps.particle;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * Explosion &amp; Firework Shield (REQ-HEAD-6).
 *
 * <p>A TNT chain, a wither spawn or a firework show can put thousands of particles
 * into the client within a fraction of a second. Every one of them is a quad that
 * has to be transformed, uploaded and drawn, and a client that tries to draw all of
 * them misses its frame budget so badly that the game becomes unplayable for a
 * second or two. The player did nothing wrong - the world simply produced more
 * particles than a single frame can draw.</p>
 *
 * <h2>What the shield does</h2>
 * <ol>
 *   <li><b>It watches five high-density categories.</b> {@link Category#EXPLOSION},
 *       {@link Category#EXPLOSION_EMITTER}, {@link Category#FIREWORK},
 *       {@link Category#CAMPFIRE_SMOKE} and {@link Category#LARGE_SMOKE} are the
 *       particles that flood a client. Every other particle is left alone, so a
 *       lava splash or a portal is not affected.</li>
 *   <li><b>It caps the burst rate.</b> A token bucket lets
 *       {@value #BURST_RATE_LIMIT} particles per second through and drops the
 *       surplus. The bucket refills continuously, so the cap is a <em>rate</em> and
 *       not a cliff: a client that is handed a thousand particles a second still
 *       shows a steady, readable explosion instead of a frozen frame.</li>
 *   <li><b>It drops the redundant overlaps.</b> A one-block cell may hold
 *       {@value #CELL_RATE_LIMIT} of these particles per second; the surplus is
 *       dropped because it is drawn on top of the particles that are already
 *       there and the player cannot tell the difference.</li>
 * </ol>
 *
 * <h2>What the shield never does</h2>
 * <p>The explosion has to stay <em>readable</em>. A client that removes the flash of
 * an explosion has not saved work the player would thank it for; it has simply made
 * the game lie. So the first particle of every category inside a burst is
 * <b>protected</b>: it bypasses the rate limit, it bypasses the per-cell limit and
 * it bypasses the global creation budget of {@link ThoriumParticleBudget}. For an
 * explosion those two protected particles are the flash and the primary puff, and
 * they survive everything.</p>
 *
 * <h2>Purity</h2>
 * <p>This class contains no Minecraft type at all. It is a pure policy engine, so
 * the whole decision table is unit-testable without a running game. The Minecraft
 * half - telling the particle types apart and applying the decision to the engine -
 * lives in {@link ExplosionParticleFilter}.</p>
 */
public final class ExplosionShield {

    /** Burst rate limit for the tracked categories, in particles per second. */
    public static final int BURST_RATE_LIMIT = 250;

    /** Per-cell redundancy limit, in particles per second per one-block cell. */
    public static final int CELL_RATE_LIMIT = 48;

    /** How long a cell has to stay quiet before it starts a new burst, in milliseconds. */
    public static final long CELL_IDLE_MS = 250L;

    /** The cost of one particle in the token buckets, in milli-particles. */
    static final int PARTICLE_COST = 1000;

    /** The capacity of the global bucket, in milli-particles. */
    static final long GLOBAL_CREDIT_LIMIT = (long) BURST_RATE_LIMIT * PARTICLE_COST;

    /** The capacity of one cell bucket, in milli-particles. */
    static final long CELL_CREDIT_LIMIT = (long) CELL_RATE_LIMIT * PARTICLE_COST;

    /** The longest refill step the global bucket applies at once, in milliseconds. */
    private static final long MAX_REFILL_MS = 1000L;

    /** The number of cells the shield tracks at the same time; a power of two. */
    private static final int CELL_SLOTS = 2048;

    /** The mask that turns a hash into a cell slot. */
    private static final int SLOT_MASK = CELL_SLOTS - 1;

    private static final AtomicLong ADMITTED = new AtomicLong();
    private static final AtomicLong CULLED_REDUNDANT = new AtomicLong();
    private static final AtomicLong CULLED_OVER_RATE = new AtomicLong();
    private static final AtomicLong PRIORITY_ADMITTED = new AtomicLong();

    /** The global burst-rate bucket, in milli-particles. */
    private static final AtomicLong GLOBAL_CREDIT = new AtomicLong(GLOBAL_CREDIT_LIMIT);

    /** When the global bucket was last refilled, in milliseconds. */
    private static final AtomicLong GLOBAL_STAMP = new AtomicLong();

    /** The cell key of each slot. */
    private static final long[] CELL_KEYS = new long[CELL_SLOTS];

    /** When each slot was last touched, in milliseconds. */
    private static final long[] CELL_STAMPS = new long[CELL_SLOTS];

    /** The per-cell token bucket of each slot, in milli-particles. */
    private static final long[] CELL_CREDIT = new long[CELL_SLOTS];

    /** The categories that already fired in the burst of each slot. */
    private static final int[] CELL_MASKS = new int[CELL_SLOTS];

    /** Whether a slot is in use. */
    private static final boolean[] CELL_USED = new boolean[CELL_SLOTS];

    private ExplosionShield() {
    }

    // --------------------------------------------------------------------
    // The particle categories
    // --------------------------------------------------------------------

    /**
     * The particle categories the shield watches.
     *
     * <p>Each constant carries a single bit, so the set of categories that already
     * fired inside a burst fits into one {@code int} mask and the whole "have we
     * already shown the flash?" question is a bit test.</p>
     */
    public enum Category {
        /** A particle the shield does not care about. */
        NONE(0),
        /** The explosion flash itself. */
        EXPLOSION(1),
        /** The primary explosion puff - the big expanding ball. */
        EXPLOSION_EMITTER(2),
        /** The firework rocket and its sparks. */
        FIREWORK(4),
        /** The smoke of a cosy or a signal campfire. */
        CAMPFIRE_SMOKE(8),
        /** The large smoke puffs an explosion leaves behind. */
        LARGE_SMOKE(16);

        private final int bit;

        Category(int bit) {
            this.bit = bit;
        }

        /** The single bit this category occupies in a burst mask. */
        public int bit() {
            return bit;
        }
    }

    // --------------------------------------------------------------------
    // The decision
    // --------------------------------------------------------------------

    /** What the shield decided to do with one particle. */
    public enum Decision {
        /** The particle is allowed through and spends a token. */
        ADMIT,
        /** The particle is allowed through and bypasses every limit. */
        ADMIT_PRIORITY,
        /** The particle is dropped: the cell already holds enough overlapping quads. */
        CULL_REDUNDANT,
        /** The particle is dropped: the burst rate limit is exhausted. */
        CULL_OVER_RATE
    }

    /**
     * The decision table, expressed without any game state.
     *
     * <p>Split out of {@link #evaluate} so the whole table can be verified without a
     * running Minecraft. The order of the tests is the order of the rules: the
     * protected particle wins over everything, then the per-cell redundancy, then
     * the global rate limit.</p>
     *
     * @param priority       whether the particle is the first of its category in its
     *                       burst - the explosion flash and the primary puff
     * @param cellSaturated  whether the particle's cell already holds its full share
     * @param overRate       whether the global burst rate limit is exhausted
     * @return the decision
     */
    static Decision decide(boolean priority, boolean cellSaturated, boolean overRate) {
        if (priority) {
            return Decision.ADMIT_PRIORITY;
        }
        if (cellSaturated) {
            return Decision.CULL_REDUNDANT;
        }
        if (overRate) {
            return Decision.CULL_OVER_RATE;
        }
        return Decision.ADMIT;
    }

    /**
     * Evaluates one particle against the shield.
     *
     * <p>This is the live path. It refreshes the particle's cell, applies the pure
     * {@link #decide} rule and books the result.</p>
     *
     * @param category the category of the particle
     * @param x        the particle's world X position
     * @param y        the particle's world Y position
     * @param z        the particle's world Z position
     * @param now      the current wall clock time in milliseconds
     * @return the decision
     */
    public static Decision evaluate(Category category, double x, double y, double z, long now) {
        if (category == null || category == Category.NONE) {
            return Decision.ADMIT;
        }
        int slot = cellSlot(cellKey(x, y, z), now);
        boolean priority = (CELL_MASKS[slot] & category.bit()) == 0;
        boolean cellSaturated = CELL_CREDIT[slot] < PARTICLE_COST;
        boolean overRate = globalCredit(now) < PARTICLE_COST;

        Decision decision = decide(priority, cellSaturated, overRate);
        if (decision == Decision.ADMIT_PRIORITY) {
            CELL_MASKS[slot] |= category.bit();
        } else if (decision == Decision.ADMIT) {
            CELL_CREDIT[slot] -= PARTICLE_COST;
            consumeGlobalCredit();
        }
        return decision;
    }

    // --------------------------------------------------------------------
    // The cell bookkeeping
    // --------------------------------------------------------------------

    /**
     * The one-block cell a world position belongs to.
     *
     * <p>The cell is one block wide in every direction: two particles inside the
     * same cell overlap far enough that the player cannot tell them apart.</p>
     *
     * @param x the world X position
     * @param y the world Y position
     * @param z the world Z position
     * @return the cell key
     */
    public static long cellKey(double x, double y, double z) {
        return (((long) cellCoordinate(x) & 0x1FFFFF) << 42)
                | (((long) cellCoordinate(y) & 0x1FFFFF) << 21)
                | (cellCoordinate(z) & 0x1FFFFF);
    }

    /**
     * The block coordinate of a world position.
     *
     * @param value the world coordinate
     * @return the block coordinate
     */
    public static int cellCoordinate(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Finds the slot of a cell, claiming or refreshing it as needed.
     *
     * <p>A cell that has been quiet for {@link #CELL_IDLE_MS} is stale and can be
     * taken over, so the table follows the player instead of filling up with the
     * cells they visited an hour ago.</p>
     *
     * @param key the cell key
     * @param now the current wall clock time in milliseconds
     * @return the slot index
     */
    private static int cellSlot(long key, long now) {
        int slot = (int) (mix(key) & SLOT_MASK);
        int stale = -1;
        for (int probe = 0; probe < CELL_SLOTS; probe++) {
            if (!CELL_USED[slot]) {
                return claim(slot, key, now);
            }
            if (CELL_KEYS[slot] == key) {
                refresh(slot, now);
                return slot;
            }
            if (stale < 0 && now - CELL_STAMPS[slot] > CELL_IDLE_MS) {
                stale = slot;
            }
            slot = (slot + 1) & SLOT_MASK;
        }
        if (stale >= 0) {
            return claim(stale, key, now);
        }
        // Every slot is busy with a live cell: start over rather than lose the shield.
        clearCells();
        return claim(0, key, now);
    }

    /** Claims a slot for a cell and gives it a full budget. */
    private static int claim(int slot, long key, long now) {
        CELL_USED[slot] = true;
        CELL_KEYS[slot] = key;
        CELL_STAMPS[slot] = now;
        CELL_CREDIT[slot] = CELL_CREDIT_LIMIT;
        CELL_MASKS[slot] = 0;
        return slot;
    }

    /**
     * Refills a cell's bucket and closes its burst if it has been quiet.
     *
     * @param slot the slot index
     * @param now  the current wall clock time in milliseconds
     */
    private static void refresh(int slot, long now) {
        long stamp = CELL_STAMPS[slot];
        long elapsed = now - stamp;
        if (elapsed > 0L) {
            if (elapsed > MAX_REFILL_MS) {
                // The bucket is full after a second anyway, and a clock jump must
                // never overflow the arithmetic.
                elapsed = MAX_REFILL_MS;
            }
            long credit = CELL_CREDIT[slot] + elapsed * CELL_RATE_LIMIT;
            if (credit > CELL_CREDIT_LIMIT) {
                credit = CELL_CREDIT_LIMIT;
            }
            CELL_CREDIT[slot] = credit;
            if (elapsed > CELL_IDLE_MS) {
                // The cell was quiet: whatever fired there belongs to an older burst.
                CELL_MASKS[slot] = 0;
            }
        }
        CELL_STAMPS[slot] = now;
    }

    /** Empties the cell table. */
    private static void clearCells() {
        Arrays.fill(CELL_USED, false);
    }

    // --------------------------------------------------------------------
    // The global burst rate bucket
    // --------------------------------------------------------------------

    /**
     * The credit the global bucket has left, refilled up to the current instant.
     *
     * <p>The bucket is measured in milli-particles so that the refill rate is an
     * exact integer: {@value #BURST_RATE_LIMIT} particles per second is
     * {@value #BURST_RATE_LIMIT} milli-particles per millisecond.</p>
     *
     * @param now the current wall clock time in milliseconds
     * @return the remaining credit in milli-particles
     */
    private static long globalCredit(long now) {
        long stamp = GLOBAL_STAMP.get();
        long elapsed = now - stamp;
        if (elapsed <= 0L) {
            return GLOBAL_CREDIT.get();
        }
        if (elapsed > MAX_REFILL_MS) {
            elapsed = MAX_REFILL_MS;
        }
        if (!GLOBAL_STAMP.compareAndSet(stamp, now)) {
            // Another thread refilled the bucket; its refill already covers this gap.
            return GLOBAL_CREDIT.get();
        }
        while (true) {
            long credit = GLOBAL_CREDIT.get();
            long updated = credit + elapsed * BURST_RATE_LIMIT;
            if (updated > GLOBAL_CREDIT_LIMIT) {
                updated = GLOBAL_CREDIT_LIMIT;
            }
            if (GLOBAL_CREDIT.compareAndSet(credit, updated)) {
                return updated;
            }
        }
    }

    /** Spends one particle from the global bucket. */
    private static void consumeGlobalCredit() {
        while (true) {
            long credit = GLOBAL_CREDIT.get();
            if (credit < PARTICLE_COST) {
                return;
            }
            if (GLOBAL_CREDIT.compareAndSet(credit, credit - PARTICLE_COST)) {
                return;
            }
        }
    }

    // --------------------------------------------------------------------
    // Lifecycle and metrics
    // --------------------------------------------------------------------

    /**
     * Announces that the explosion shield is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from "not
     * available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.EXPLOSION_SHIELD.markWired();
    }

    /** Whether the player has the explosion shield switched on. */
    public static boolean isEnabled() {
        return ThoriumFeatureToggles.isExplosionShieldEnabled();
    }

    /** Books a particle that the shield allowed through. */
    static void recordAdmitted() {
        ADMITTED.incrementAndGet();
    }

    /** Books a particle that was dropped because its cell was saturated. */
    static void recordRedundantCull() {
        CULLED_REDUNDANT.incrementAndGet();
    }

    /** Books a particle that was dropped because the burst rate limit was exhausted. */
    static void recordOverRateCull() {
        CULLED_OVER_RATE.incrementAndGet();
    }

    /** Books a particle that bypassed every limit. */
    static void recordPriorityAdmitted() {
        PRIORITY_ADMITTED.incrementAndGet();
        ADMITTED.incrementAndGet();
    }

    /** Particles the shield let through. */
    public static long getAdmitted() {
        return ADMITTED.get();
    }

    /** Particles dropped because their cell already held enough overlapping quads. */
    public static long getCulledRedundant() {
        return CULLED_REDUNDANT.get();
    }

    /** Particles dropped because the burst rate limit was exhausted. */
    public static long getCulledOverRate() {
        return CULLED_OVER_RATE.get();
    }

    /** Particles that bypassed every limit - the explosion flash and the puff. */
    public static long getPriorityAdmitted() {
        return PRIORITY_ADMITTED.get();
    }

    /**
     * A one-line description of what the shield has done so far.
     *
     * <p>Used by the startup subsystem report so the log shows the real state of the
     * feature instead of a static claim (REQ-DIAG-1).</p>
     */
    public static String describeActivity() {
        return getAdmitted() + " admitted, "
                + (getCulledRedundant() + getCulledOverRate()) + " culled, "
                + getPriorityAdmitted() + " protected";
    }

    /**
     * Resets the counters, the buckets and the cell table.
     *
     * <p>Test seam: the shield is process-wide, so a test that culls a particle has
     * to be able to put everything back.</p>
     */
    public static void resetMetrics() {
        ADMITTED.set(0L);
        CULLED_REDUNDANT.set(0L);
        CULLED_OVER_RATE.set(0L);
        PRIORITY_ADMITTED.set(0L);
        GLOBAL_CREDIT.set(GLOBAL_CREDIT_LIMIT);
        GLOBAL_STAMP.set(0L);
        clearCells();
    }

    /** The splitmix64 finalizer: a cheap, well distributed mixer. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
