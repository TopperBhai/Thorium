package dev.hyperionfps.gui;

/**
 * A tiny toast queue for the in-game HUD.
 *
 * <p>The toast is how Thorium tells the player something that happened <em>now</em>
 * - a stutter attribution, a mode change - without opening a screen or writing to
 * the chat log. It lives in shared code so that all five per-version HUD renderers
 * show exactly the same thing.</p>
 *
 * <p>The queue is a fixed ring of four entries: it never grows, never allocates a
 * collection and never blocks (NFR-1, NFR-2).</p>
 */
public final class ThoriumToastQueue {

    /** How many toasts can be pending at the same time. */
    public static final int CAPACITY = 4;

    /** How long a toast stays on screen by default, in nanoseconds. */
    public static final long DEFAULT_DURATION_NANOS = 4_000_000_000L;

    private static final String[] MESSAGES = new String[CAPACITY];
    private static final long[] EXPIRY = new long[CAPACITY];
    private static int next;

    private ThoriumToastQueue() {
    }

    /** Queues a toast with the default lifetime. */
    public static void offer(String message) {
        offer(message, DEFAULT_DURATION_NANOS);
    }

    /**
     * Queues a toast.
     *
     * @param message       the text to show; a blank message is ignored
     * @param durationNanos how long it stays on screen
     */
    public static synchronized void offer(String message, long durationNanos) {
        if (message == null || message.isBlank()) {
            return;
        }
        MESSAGES[next] = message;
        EXPIRY[next] = System.nanoTime() + Math.max(durationNanos, 0L);
        next = (next + 1) % CAPACITY;
    }

    /** The toast that should currently be on screen, or an empty string. */
    public static synchronized String activeToast() {
        return activeToast(System.nanoTime());
    }

    /** The toast that should currently be on screen, or an empty string. */
    public static synchronized String activeToast(long nowNanos) {
        // The newest entry is the one to show, so walk backwards from it.
        for (int i = 1; i <= CAPACITY; i++) {
            int index = ((next - i) % CAPACITY + CAPACITY) % CAPACITY;
            String message = MESSAGES[index];
            if (message == null) {
                continue;
            }
            if (nowNanos <= EXPIRY[index]) {
                return message;
            }
        }
        return "";
    }

    /** Removes every pending toast. */
    public static synchronized void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            MESSAGES[i] = null;
            EXPIRY[i] = 0L;
        }
        next = 0;
    }
}
