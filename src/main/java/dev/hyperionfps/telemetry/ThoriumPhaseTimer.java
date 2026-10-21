package dev.hyperionfps.telemetry;

/**
 * Per-frame breakdown of where the client spent its time.
 *
 * <p>The Stutter Hunter has to answer one question when the game freezes: <em>what
 * was the game doing while it froze?</em> A frametime alone cannot answer it - 58 ms
 * is 58 ms whether it was garbage collection, a hundred mobs animating or a chunk
 * upload. This class collects the individual pieces so the hunter can name the
 * dominant one.</p>
 *
 * <h2>The five phases</h2>
 * <ul>
 *   <li><b>Tick</b> - the client tick: input handling, entity ticks, block updates.</li>
 *   <li><b>Entities</b> - the entity and block-entity visibility decisions.</li>
 *   <li><b>Chunk</b> - the chunk mesh uploads.</li>
 *   <li><b>Render</b> - everything the world renderer does that is not one of the
 *       two above: terrain, particles, the buffer swap.</li>
 *   <li><b>Swap</b> - the frame pacing wait, i.e. the time the client deliberately
 *       spent sleeping to hold the target framerate.</li>
 * </ul>
 *
 * <p>Every number is a plain volatile field: reading the breakdown never blocks and
 * never allocates (NFR-1, NFR-2). The clock is {@link System#nanoTime()}, never
 * wall-clock time (NFR-6).</p>
 */
public final class ThoriumPhaseTimer {

    /** The number of tracked phases. */
    public static final int PHASE_COUNT = 5;

    /** The client tick. */
    public static final int PHASE_TICK = 0;

    /** The entity and block-entity visibility decisions. */
    public static final int PHASE_ENTITIES = 1;

    /** The chunk mesh uploads. */
    public static final int PHASE_CHUNK_UPLOAD = 2;

    /** The world render, excluding the two phases above. */
    public static final int PHASE_RENDER = 3;

    /** The frame pacing wait. */
    public static final int PHASE_SWAP = 4;

    private static final String[] PHASE_NAMES = {"Tick", "Entities", "Chunk", "Render", "Swap"};

    private static volatile long clientStartNanos;
    private static volatile long tickNanos;
    private static volatile long swapNanos;
    private static volatile long entitiesNanos;
    private static volatile long chunkUploadNanos;
    private static volatile long renderStartNanos;
    private static volatile long measuredRenderNanos;

    private ThoriumPhaseTimer() {
    }

    // --------------------------------------------------------------------
    // Collection
    // --------------------------------------------------------------------

    /**
     * Starts a new frame and clears the breakdown.
     *
     * @param nowNanos the current monotonic timestamp
     */
    public static void beginFrame(long nowNanos) {
        clientStartNanos = nowNanos;
        tickNanos = 0L;
        swapNanos = 0L;
        entitiesNanos = 0L;
        chunkUploadNanos = 0L;
        renderStartNanos = 0L;
    }

    /**
     * Records that the world render has started.
     *
     * <p>Everything between the client frame start and this point is the client
     * tick, minus the frame pacing wait, which is measured separately.</p>
     *
     * @param nowNanos the current monotonic timestamp
     */
    public static void markWorldFrameStart(long nowNanos) {
        renderStartNanos = nowNanos;
        long elapsed = nowNanos - clientStartNanos - swapNanos;
        tickNanos = elapsed > 0L ? elapsed : 0L;
    }

    /** Whether the world frame has been entered or measured. */
    public static boolean hasWorldFrame() {
        return renderStartNanos > 0L || entitiesNanos > 0L || chunkUploadNanos > 0L;
    }

    /** Adds the time the frame pacer spent waiting. */
    public static void addSwap(long nanos) {
        if (nanos > 0L) {
            swapNanos += nanos;
        }
    }

    /** Adds the time spent on entity and block-entity visibility decisions. */
    public static void addEntities(long nanos) {
        if (nanos > 0L) {
            entitiesNanos += nanos;
        }
    }

    /** Adds the time spent on chunk mesh uploads. */
    public static void addChunkUpload(long nanos) {
        if (nanos > 0L) {
            chunkUploadNanos += nanos;
        }
    }

    // --------------------------------------------------------------------
    // Reading
    // --------------------------------------------------------------------

    /** The client tick time of the current frame, in nanoseconds. */
    public static long tickNanos() {
        return tickNanos;
    }

    /** The entity dispatch time of the current frame, in nanoseconds. */
    public static long entitiesNanos() {
        return entitiesNanos;
    }

    /** The chunk upload time of the current frame, in nanoseconds. */
    public static long chunkUploadNanos() {
        return chunkUploadNanos;
    }

    /** The frame pacing wait of the current frame, in nanoseconds. */
    public static long swapNanos() {
        return swapNanos;
    }

    /**
     * The world render time of the frame, in nanoseconds.
     *
     * <p>The render is whatever is left of the frame once the tick and the pacing
     * wait are accounted for. The entity and chunk phases are <em>inside</em> the
     * render, so they are subtracted here to keep the buckets disjoint.</p>
     *
     * @param frameNanos the total duration of the frame
     */
    public static long renderNanos(long frameNanos) {
        long render = frameNanos - tickNanos - swapNanos - entitiesNanos - chunkUploadNanos;
        return render > 0L ? render : 0L;
    }

    /** The name of a phase. */
    public static String phaseName(int phase) {
        return phase >= 0 && phase < PHASE_NAMES.length ? PHASE_NAMES[phase] : "Unknown";
    }

    /**
     * The time of a phase inside the given frame.
     *
     * @param frameNanos the total duration of the frame
     */
    public static long phaseNanos(int phase, long frameNanos) {
        return switch (phase) {
            case PHASE_TICK -> tickNanos;
            case PHASE_ENTITIES -> entitiesNanos;
            case PHASE_CHUNK_UPLOAD -> chunkUploadNanos;
            case PHASE_RENDER -> renderNanos(frameNanos);
            case PHASE_SWAP -> swapNanos;
            default -> 0L;
        };
    }

    /** Clears every measurement. */
    public static void reset() {
        beginFrame(System.nanoTime());
    }
}
