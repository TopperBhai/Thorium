package dev.hyperionfps.audio;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.anim.MobFarmOptimizer;
import dev.hyperionfps.mixin.core.AbstractSoundInstanceAccessor;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Beyond-Sodium Sound Engine Governor.
 *
 * <p>Prevents OpenAL audio thread choking and frame hitching caused by hundreds of
 * duplicate mob sounds (villager trading halls, cow breeding pens, zombie farms)
 * or rapid redstone piston loops firing simultaneously.</p>
 *
 * <p>Enforces a spatial rate limit: maximum 4 identical/localized sounds per 8-block
 * cell per 50ms window. Critical audio (Player, Music, Records, UI, Bosses) is never clamped.</p>
 *
 * <h2>Dynamic acoustic dampening</h2>
 * <p>A mob farm is the one case where the general rate limit is the wrong tool. The
 * sounds are real - thirty zombies really are growling - and cutting them off at four
 * per tick makes the farm fall silent instead of cheaper. So a cell that the
 * {@link MobFarmOptimizer} reports as a mob farm gets its own policy:</p>
 * <ul>
 *   <li>the first {@value #CLUSTER_EVENT_BUDGET} events of a 50 ms window play at
 *       their normal volume,</li>
 *   <li>the next {@value #CLUSTER_EVENT_BUDGET} are <em>dampened</em> to
 *       {@value #CLUSTER_VOLUME_FACTOR} of their volume,</li>
 *   <li>and everything beyond that is dropped as a surplus duplicate.</li>
 * </ul>
 * <p>The dampening is applied to the sound instance itself, so the audio thread
 * really does play a quieter sound instead of a louder one that the player then has
 * to fight with the mixer slider.</p>
 *
 * <p>The dampening needs the mob farm cluster map, so it only engages while the
 * Mob Farm Optimizer is switched on. That is not an accident: the map is the only
 * place that knows which cell is a farm, and it is expensive enough that it should
 * not be built for a feature nobody uses.</p>
 */
public final class ThoriumSoundGovernor {

    /** Maximum sounds of one kind per spatial cell per tick. */
    public static final int CELL_SOUND_LIMIT = 4;

    /** Sound events of one mob farm cluster per tick before the ambience is dampened. */
    public static final int CLUSTER_EVENT_BUDGET = 6;

    /** The factor a dampened sound is multiplied with. */
    public static final float CLUSTER_VOLUME_FACTOR = 0.5f;

    /** Sounds quieter than this are dropped instead of dampened. */
    public static final float MIN_DAMPENED_VOLUME = 0.05f;

    /** The mob farm policy: play the sound at its normal volume. */
    static final int CLUSTER_ACTION_FULL = 0;

    /** The mob farm policy: play the sound at a reduced volume. */
    static final int CLUSTER_ACTION_DAMPEN = 1;

    /** The mob farm policy: drop the sound. */
    static final int CLUSTER_ACTION_DROP = 2;

    private static volatile boolean enabled = true;
    private static final AtomicLong SOUNDS_SUPPRESSED = new AtomicLong(0);
    private static final AtomicLong CLUSTER_SOUNDS_DAMPENED = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, CellCounter> SPATIAL_SOUND_MAP = new ConcurrentHashMap<>(256);
    private static final ConcurrentHashMap<Long, CellCounter> CLUSTER_SOUND_MAP = new ConcurrentHashMap<>(64);

    private ThoriumSoundGovernor() {
    }

    /**
     * Evaluates whether a SoundInstance should be suppressed before hitting the OpenAL mixer.
     */
    public static boolean shouldSuppressSound(SoundInstance sound) {
        if (!enabled || sound == null) {
            return false;
        }

        SoundSource source = sound.getSource();
        // Never suppress player actions, music, records, or voice
        if (source == SoundSource.MASTER || source == SoundSource.MUSIC ||
            source == SoundSource.RECORDS || source == SoundSource.PLAYERS ||
            source == SoundSource.VOICE) {
            return false;
        }

        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();
        long now = System.currentTimeMillis();

        // A mob farm has its own policy: the sounds are legitimate, so they are
        // dampened rather than cut off. The general rate limit is replaced by it.
        if (MobFarmOptimizer.isDenseCell(x, z)) {
            return governClusterSound(sound, x, z, now);
        }

        // 8x8x8 meter spatial cell hash
        long cellKey = spatialCellKey(x, y, z);
        CellCounter counter = counterFor(SPATIAL_SOUND_MAP, cellKey, now);

        // Periodic map cleanup if it grows too large
        if (SPATIAL_SOUND_MAP.size() > 512) {
            SPATIAL_SOUND_MAP.entrySet().removeIf(e -> (now - e.getValue().lastWindowMs) > 1000L);
        }

        if (counter.recordAndCheckLimit(now)) {
            SOUNDS_SUPPRESSED.incrementAndGet();
            return true;
        }

        return false;
    }

    /**
     * Applies the mob farm policy to one sound.
     *
     * @param sound the sound instance
     * @param x     the sound's world X position
     * @param z     the sound's world Z position
     * @param now   the current wall clock time in milliseconds
     * @return whether the sound was dropped
     */
    private static boolean governClusterSound(SoundInstance sound, double x, double z, long now) {
        long cellKey = clusterCellKey(x, z);
        CellCounter counter = counterFor(CLUSTER_SOUND_MAP, cellKey, now);
        if (CLUSTER_SOUND_MAP.size() > 256) {
            CLUSTER_SOUND_MAP.entrySet().removeIf(e -> (now - e.getValue().lastWindowMs) > 1000L);
        }

        int action = clusterAction(counter.record(now));
        if (action == CLUSTER_ACTION_FULL) {
            // Within the budget: the farm is heard at its normal volume.
            return false;
        }
        if (action == CLUSTER_ACTION_DROP) {
            // The surplus duplicates are dropped outright.
            SOUNDS_SUPPRESSED.incrementAndGet();
            return true;
        }

        // Dampen: the same sound, quieter.
        float volume = sound.getVolume() * CLUSTER_VOLUME_FACTOR;
        if (volume < MIN_DAMPENED_VOLUME || !(sound instanceof AbstractSoundInstance)) {
            SOUNDS_SUPPRESSED.incrementAndGet();
            return true;
        }
        try {
            ((AbstractSoundInstanceAccessor) sound).thorium$setVolume(volume);
        } catch (Throwable unavailable) {
            // The accessor mixin did not apply on this client. The sound is dropped
            // instead: a rendering client must never fail because of a sound.
            SOUNDS_SUPPRESSED.incrementAndGet();
            return true;
        }
        CLUSTER_SOUNDS_DAMPENED.incrementAndGet();
        return false;
    }

    /**
     * Looks up the counter of a cell, creating it if it does not exist yet.
     *
     * <p>Written without {@code computeIfAbsent} on purpose: the mapping function of
     * {@code computeIfAbsent} would have to capture the timestamp, and that allocates
     * a lambda object on every single sound.</p>
     */
    private static CellCounter counterFor(ConcurrentHashMap<Long, CellCounter> map, long key, long now) {
        CellCounter counter = map.get(key);
        if (counter != null) {
            return counter;
        }
        CellCounter created = new CellCounter(now);
        CellCounter existing = map.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }

    /** The 8x8x8 spatial cell key of a position. */
    private static long spatialCellKey(double x, double y, double z) {
        int gx = ((int) Math.floor(x)) >> 3;
        int gy = ((int) Math.floor(y)) >> 3;
        int gz = ((int) Math.floor(z)) >> 3;
        return (((long) gx & 0x1FFFFF) << 42) | (((long) gy & 0x1FFFFF) << 21) | ((long) gz & 0x1FFFFF);
    }

    /** The horizontal cluster cell key of a position. */
    private static long clusterCellKey(double x, double z) {
        int gx = MobFarmOptimizer.cellCoordinate(x);
        int gz = MobFarmOptimizer.cellCoordinate(z);
        return ((long) (gx & 0xFFFFF) << 20) | (gz & 0x1FFFFF);
    }

    public static long getSoundsSuppressed() {
        return SOUNDS_SUPPRESSED.get();
    }

    /** How many mob farm sounds have been dampened. */
    public static long getClusterSoundsDampened() {
        return CLUSTER_SOUNDS_DAMPENED.get();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * The rate limit of the general path, expressed without any game state.
     *
     * @param events the number of events in the current window
     * @return whether the event is over the limit
     */
    static boolean exceedsCellLimit(int events) {
        return events > CELL_SOUND_LIMIT;
    }

    /**
     * The mob farm policy, expressed without any game state.
     *
     * @param events the number of events in the current window
     * @return {@link #CLUSTER_ACTION_FULL} to play at full volume,
     *         {@link #CLUSTER_ACTION_DAMPEN} to dampen, {@link #CLUSTER_ACTION_DROP}
     *         to drop
     */
    static int clusterAction(int events) {
        if (events <= CLUSTER_EVENT_BUDGET) {
            return CLUSTER_ACTION_FULL;
        }
        if (events > CLUSTER_EVENT_BUDGET * 2) {
            return CLUSTER_ACTION_DROP;
        }
        return CLUSTER_ACTION_DAMPEN;
    }

    private static final class CellCounter {
        private volatile long lastWindowMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private CellCounter(long startTimeMs) {
            this.lastWindowMs = startTimeMs;
        }

        private boolean recordAndCheckLimit(long now) {
            return exceedsCellLimit(record(now));
        }

        /**
         * Counts one event in the current window.
         *
         * @param now the current wall clock time in milliseconds
         * @return the number of events in the window, starting at one
         */
        private int record(long now) {
            if (now - lastWindowMs >= 50L) { // 50ms window = 1 Minecraft game tick
                lastWindowMs = now;
                count.set(1);
                return 1;
            }
            return count.incrementAndGet();
        }
    }
}
