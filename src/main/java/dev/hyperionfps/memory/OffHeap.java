package dev.hyperionfps.memory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic off-heap release for direct byte buffers.
 *
 * <p>A garbage-collected direct buffer keeps its native allocation until its
 * internal cleaner happens to run - which on a memory-busy client can lag the
 * collection by minutes. Pools that cache buffers must therefore be able to
 * hand memory back on demand instead of hoping the collector gets around to
 * it. The JDK exposes exactly that ability as
 * {@code sun.misc.Unsafe.invokeCleaner(ByteBuffer)}, reachable by reflection
 * because {@code jdk.unsupported} exports and opens {@code sun.misc} on every
 * stock JVM - no {@code --add-opens} flags required.</p>
 *
 * <p>Everything here fails soft: when the mechanism is unavailable (a future
 * JVM removing {@code sun.misc}, a hardened runtime), releases simply degrade
 * to ordinary garbage collection, which is the status quo the pools would
 * have had anyway.</p>
 */
public final class OffHeap {

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/OffHeap");

    private static final Cleaner CLEANER = resolveCleaner();

    private OffHeap() {
    }

    /** Bound {@code Unsafe} instance plus its {@code invokeCleaner} method. */
    private record Cleaner(Object unsafe, Method invokeCleaner) {
    }

    private static Cleaner resolveCleaner() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
            return new Cleaner(unsafe, invokeCleaner);
        } catch (Throwable unavailable) {
            LOGGER.debug("Deterministic off-heap release unavailable on this JVM: {}", unavailable.toString());
            return null;
        }
    }

    /** Whether deterministic release is possible on this JVM. */
    public static boolean available() {
        return CLEANER != null;
    }

    /**
     * Frees a direct buffer's native memory immediately.
     *
     * <p>The caller hands over ownership: the buffer must not be touched after
     * this method returns {@code true}. Buffers already freed by their own
     * cleaner are reported as failures without throwing.</p>
     *
     * @return {@code true} if the memory was released deterministically
     */
    public static boolean releaseDirect(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return false;
        }
        Cleaner cleaner = CLEANER;
        if (cleaner == null) {
            return false;
        }
        try {
            cleaner.invokeCleaner().invoke(cleaner.unsafe(), buffer);
            return true;
        } catch (Throwable failure) {
            LOGGER.debug("Off-heap release deferred to the garbage collector: {}", failure.toString());
            return false;
        }
    }
}
