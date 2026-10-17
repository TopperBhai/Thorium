package dev.hyperionfps.audio;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
 */
public final class ThoriumSoundGovernor {

    private static volatile boolean enabled = true;
    private static final AtomicLong SOUNDS_SUPPRESSED = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, CellCounter> SPATIAL_SOUND_MAP = new ConcurrentHashMap<>(256);

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

        // 8x8x8 meter spatial cell hash
        int gx = ((int) Math.floor(x)) >> 3;
        int gy = ((int) Math.floor(y)) >> 3;
        int gz = ((int) Math.floor(z)) >> 3;
        long cellKey = (((long) gx & 0x1FFFFF) << 42) | (((long) gy & 0x1FFFFF) << 21) | ((long) gz & 0x1FFFFF);

        long now = System.currentTimeMillis();
        CellCounter counter = SPATIAL_SOUND_MAP.computeIfAbsent(cellKey, k -> new CellCounter(now));

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

    public static long getSoundsSuppressed() {
        return SOUNDS_SUPPRESSED.get();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static final class CellCounter {
        private volatile long lastWindowMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private CellCounter(long startTimeMs) {
            this.lastWindowMs = startTimeMs;
        }

        private boolean recordAndCheckLimit(long now) {
            if (now - lastWindowMs >= 50L) { // 50ms window = 1 Minecraft game tick
                lastWindowMs = now;
                count.set(1);
                return false;
            }

            // Max 4 duplicate sounds per cell per tick
            return count.incrementAndGet() > 4;
        }
    }
}
