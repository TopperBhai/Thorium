package dev.hyperionfps.chunk;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The Elytra Flight Smoother's policy engine (REQ-HEAD-3).
 *
 * <p>An elytra flight moves the player faster than the chunk pipeline can build the
 * world ahead of them. At 20 blocks per second the player covers a block every tick,
 * so the sections that are <em>behind</em> the flight vector are pure waste: they were
 * already built, they are already on the screen behind the camera, and building them
 * any earlier only steals the frame time the world <em>ahead</em> needs.</p>
 *
 * <p>This class owns the decision and nothing else. It knows:</p>
 *
 * <ul>
 *   <li>the camera position of the current frame,</li>
 *   <li>the player's velocity vector {@code v},</li>
 *   <li>how fast "fast" is - {@value #FAST_FLIGHT_SPEED_THRESHOLD} blocks per second,</li>
 *   <li>and how wide the forward cone is - {@value #FORWARD_CONE_DEGREES} degrees.</li>
 * </ul>
 *
 * <p>A section is <b>forward</b> when the direction from the camera to the section is
 * within 45 degrees of the flight vector, <b>behind</b> when it is more than 45 degrees
 * away from the vector in the opposite direction, and <b>neutral</b> in between. Only
 * the behind-sections are deferred; the neutral ones are left alone, because the
 * player may well be looking at them.</p>
 *
 * <h2>Why the distance guard exists</h2>
 * <p>The classification uses the <em>velocity</em> vector, not the view direction. A
 * section that is behind the player in the flight direction can still be on the screen
 * when the player looks back. Deferring a section right next to the camera would
 * therefore punch a visible hole into the world, so a section closer than
 * {@value #MIN_DEFER_DISTANCE} blocks is never deferred, and the pacer additionally
 * releases anything that has been waiting for too long.</p>
 *
 * <p>This is a pure policy class: it holds no Minecraft type, so the whole decision
 * table is unit-testable without a running game (AGENTS.md §9).</p>
 */
public final class ElytraFlightSmoother {

    /**
     * The speed above which the player counts as flying fast, in blocks per second.
     *
     * <p>15 m/s is roughly the speed of a full-speed elytra dive and comfortably
     * above sprinting, so an ordinary sprint never triggers the feature.</p>
     */
    public static final double FAST_FLIGHT_SPEED_THRESHOLD = 15.0;

    /** The half-angle of the forward cone, in degrees. */
    public static final double FORWARD_CONE_DEGREES = 45.0;

    /** {@code cos(45 degrees)} - the dot-product threshold of the forward cone. */
    public static final double FORWARD_CONE_COSINE = 0.7071067811865476;

    /** The closest a section may be before it is no longer deferred, in blocks. */
    public static final double MIN_DEFER_DISTANCE = 16.0;

    /** {@link #MIN_DEFER_DISTANCE} squared, so the test never needs a square root. */
    public static final double MIN_DEFER_DISTANCE_SQUARED = MIN_DEFER_DISTANCE * MIN_DEFER_DISTANCE;

    /**
     * How a section relates to the flight vector.
     *
     * <p>The order is the priority order: a forward section is released first, a
     * behind-section last.</p>
     */
    public enum Priority {
        /** Within {@value #FORWARD_CONE_DEGREES} degrees of the flight vector. */
        FORWARD,
        /** Neither in front of nor behind the flight vector. */
        NEUTRAL,
        /** More than {@value #FORWARD_CONE_DEGREES} degrees behind the flight vector. */
        BEHIND
    }

    private static final AtomicLong FAST_FLIGHT_FRAMES = new AtomicLong();
    private static final AtomicLong DEFERRED_BEHIND = new AtomicLong();

    /** The camera position of the current frame. */
    private static volatile double cameraX;
    private static volatile double cameraY;
    private static volatile double cameraZ;

    /** The player's velocity vector of the current frame. */
    private static volatile double velocityX;
    private static volatile double velocityY;
    private static volatile double velocityZ;

    /**
     * The length of the velocity vector, cached so that the hot path never has to
     * take a square root.
     */
    private static volatile double speed;

    private ElytraFlightSmoother() {
    }

    // --------------------------------------------------------------------
    // Published state
    // --------------------------------------------------------------------

    /** Publishes the camera position of the current frame. */
    public static void publishCamera(double x, double y, double z) {
        cameraX = x;
        cameraY = y;
        cameraZ = z;
    }

    /**
     * Publishes the player's velocity vector of the current frame.
     *
     * <p>A frame that is observed while the player is flying fast is counted, so the
     * startup report can say whether the feature ever saw a flight at all.</p>
     */
    public static void publishVelocity(double vx, double vy, double vz) {
        velocityX = vx;
        velocityY = vy;
        velocityZ = vz;
        speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (isFastFlight()) {
            FAST_FLIGHT_FRAMES.incrementAndGet();
        }
    }

    /** Forgets the velocity, e.g. when the world is left. */
    public static void clearVelocity() {
        velocityX = 0.0;
        velocityY = 0.0;
        velocityZ = 0.0;
        speed = 0.0;
    }

    /** The camera X of the current frame. */
    public static double cameraX() {
        return cameraX;
    }

    /** The camera Y of the current frame. */
    public static double cameraY() {
        return cameraY;
    }

    /** The camera Z of the current frame. */
    public static double cameraZ() {
        return cameraZ;
    }

    /** The X component of the player's velocity vector. */
    public static double velocityX() {
        return velocityX;
    }

    /** The Y component of the player's velocity vector. */
    public static double velocityY() {
        return velocityY;
    }

    /** The Z component of the player's velocity vector. */
    public static double velocityZ() {
        return velocityZ;
    }

    /** The player's current speed, in blocks per second. */
    public static double speed() {
        return speed;
    }

    /**
     * Whether the player is currently flying fast enough for the smoother to act.
     *
     * <p>The threshold is strict: exactly 15 m/s is not "faster than 15 m/s".</p>
     */
    public static boolean isFastFlight() {
        return speed() > FAST_FLIGHT_SPEED_THRESHOLD;
    }

    // --------------------------------------------------------------------
    // The decision
    // --------------------------------------------------------------------

    /**
     * Classifies a direction against a flight vector.
     *
     * <p>This is the pure core of the feature: it takes the offset from the camera to
     * the section and the flight vector, and answers with the priority. It reads no
     * state, so the whole decision table is testable without a game.</p>
     *
     * @param dx the section offset from the camera
     * @param dy the section offset from the camera
     * @param dz the section offset from the camera
     * @param vx the flight vector's X component
     * @param vy the flight vector's Y component
     * @param vz the flight vector's Z component
     * @return the priority of that direction
     */
    static Priority classify(double dx, double dy, double dz, double vx, double vy, double vz) {
        double lengthSq = dx * dx + dy * dy + dz * dz;
        double velocitySq = vx * vx + vy * vy + vz * vz;
        if (lengthSq <= 0.0 || velocitySq <= 0.0) {
            // No direction, or no flight: nothing can be in front of or behind it.
            return Priority.NEUTRAL;
        }
        double dot = (dx * vx + dy * vy + dz * vz) / (Math.sqrt(lengthSq) * Math.sqrt(velocitySq));
        if (dot >= FORWARD_CONE_COSINE) {
            return Priority.FORWARD;
        }
        if (dot <= -FORWARD_CONE_COSINE) {
            return Priority.BEHIND;
        }
        return Priority.NEUTRAL;
    }

    /**
     * Classifies the direction from the camera to a section against the published
     * flight vector.
     *
     * @param x the section's block X
     * @param y the section's block Y
     * @param z the section's block Z
     * @return the priority of that section
     */
    public static Priority classify(int x, int y, int z) {
        return classify(x - cameraX, y - cameraY, z - cameraZ, velocityX, velocityY, velocityZ);
    }

    /**
     * Whether a section lies inside the 45 degree forward cone of the flight vector.
     *
     * @param x the section's block X
     * @param y the section's block Y
     * @param z the section's block Z
     * @return {@code true} when the section is in front of the player
     */
    public static boolean isInForwardCone(int x, int y, int z) {
        return classify(x, y, z) == Priority.FORWARD;
    }

    /**
     * Whether a section may be deferred until the player has flown past it.
     *
     * <p>The rules, in order:</p>
     * <ol>
     *   <li>the player must be flying fast - otherwise nothing is deferred at all,</li>
     *   <li>the section must be behind the flight vector,</li>
     *   <li>and the section must be far enough away that the player cannot be looking
     *       at it right now.</li>
     * </ol>
     *
     * @param x the section's block X
     * @param y the section's block Y
     * @param z the section's block Z
     * @return {@code true} when the section may be deferred
     */
    public static boolean shouldDefer(int x, int y, int z) {
        if (!isFastFlight()) {
            return false;
        }
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        if (dx * dx + dy * dy + dz * dz < MIN_DEFER_DISTANCE_SQUARED) {
            return false;
        }
        return classify(dx, dy, dz, velocityX, velocityY, velocityZ) == Priority.BEHIND;
    }

    // --------------------------------------------------------------------
    // Metrics
    // --------------------------------------------------------------------

    /** How many frames were observed while the player was flying fast. */
    public static long getFastFlightFrames() {
        return FAST_FLIGHT_FRAMES.get();
    }

    /** How many sections were deferred because they were behind the player. */
    public static long getDeferredBehind() {
        return DEFERRED_BEHIND.get();
    }

    /** Counts a deferred behind-section. */
    static void countDeferredBehind() {
        DEFERRED_BEHIND.incrementAndGet();
    }

    /** A one-line description of what the smoother has done so far. */
    public static String describeActivity() {
        return FAST_FLIGHT_FRAMES.get() + " fast-flight frames, "
                + DEFERRED_BEHIND.get() + " sections deferred behind the player";
    }

    /**
     * Restores the initial state.
     *
     * <p>Test seam only: the shared singletons live for the whole JVM, so a test that
     * publishes a velocity has to be able to undo it.</p>
     */
    public static void resetForTests() {
        cameraX = 0.0;
        cameraY = 0.0;
        cameraZ = 0.0;
        clearVelocity();
        FAST_FLIGHT_FRAMES.set(0L);
        DEFERRED_BEHIND.set(0L);
    }
}
