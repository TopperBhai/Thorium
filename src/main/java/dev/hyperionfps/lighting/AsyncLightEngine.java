package dev.hyperionfps.lighting;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async Light Propagation and Update Engine.
 *
 * <p>Client-side light changes (torches, lava flows, redstone lamps, day/night cycle transitions)
 * traditionally trigger costly synchronous breadth-first propagation on the main client thread,
 * causing micro-stutters and hitching in caves and Nether fortresses.
 * AsyncLightEngine queues and dispatches light propagation jobs to a dedicated low-latency
 * daemon worker, eliminating main-thread light calculation stalls.</p>
 */
public final class AsyncLightEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/AsyncLightEngine");
    private static final AsyncLightEngine INSTANCE = new AsyncLightEngine();

    public static final int LIGHT_TYPE_BLOCK = 0;
    public static final int LIGHT_TYPE_SKY = 1;

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<LightTask> taskQueue = new ConcurrentLinkedQueue<>();

    private final AtomicLong queuedUpdates = new AtomicLong(0);
    private final AtomicLong processedUpdates = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);

    private Thread workerThread;
    private volatile LightUpdateConsumer activeConsumer;

    private AsyncLightEngine() {
    }

    public static AsyncLightEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the background light propagation worker daemon.
     */
    public synchronized void init() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::runWorkerLoop, "Thorium-LightEngine");
            workerThread.setDaemon(true);
            workerThread.setPriority(Thread.NORM_PRIORITY);
            workerThread.start();
            LOGGER.info("Async Light Propagation Engine initialized and worker online");
        }
    }

    /**
     * Registers a callback consumer for executing processed light updates.
     */
    public void setConsumer(LightUpdateConsumer consumer) {
        this.activeConsumer = consumer;
    }

    /**
     * Submits a light update task asynchronously without blocking the caller.
     */
    public void enqueue(long blockPosKey, int lightType, int lightLevel) {
        if (!enabled.get()) {
            return;
        }
        taskQueue.offer(new LightTask(blockPosKey, lightType, lightLevel));
        queuedUpdates.incrementAndGet();
        if (workerThread != null) {
            LockSupport.unpark(workerThread);
        }
    }

    private void runWorkerLoop() {
        while (running.get()) {
            if (!enabled.get() || taskQueue.isEmpty()) {
                LockSupport.parkNanos(10_000_000L); // Park for up to 10ms or until unparked
                continue;
            }

            processBatch(256, activeConsumer);
        }
    }

    /**
     * Drains and processes up to {@code maxUpdates} from the queue.
     */
    public int processBatch(int maxUpdates, LightUpdateConsumer consumer) {
        int count = 0;
        LightTask task;
        while (count < maxUpdates && (task = taskQueue.poll()) != null) {
            if (consumer != null) {
                try {
                    consumer.onLightUpdate(task.blockPosKey, task.lightType, task.lightLevel);
                } catch (Throwable t) {
                    LOGGER.warn("Error processing async light update for pos {}", task.blockPosKey, t);
                }
            }
            count++;
        }

        if (count > 0) {
            processedUpdates.addAndGet(count);
            batchesProcessed.incrementAndGet();
        }
        return count;
    }

    /**
     * Gracefully stops the worker thread.
     */
    public synchronized void shutdown() {
        if (running.compareAndSet(true, false)) {
            if (workerThread != null) {
                workerThread.interrupt();
                workerThread = null;
            }
            taskQueue.clear();
        }
    }

    public static final class LightTask {
        public final long blockPosKey;
        public final int lightType;
        public final int lightLevel;

        public LightTask(long blockPosKey, int lightType, int lightLevel) {
            this.blockPosKey = blockPosKey;
            this.lightType = lightType;
            this.lightLevel = lightLevel;
        }
    }

    @FunctionalInterface
    public interface LightUpdateConsumer {
        void onLightUpdate(long blockPosKey, int lightType, int lightLevel);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPendingCount() {
        return taskQueue.size();
    }

    public long getQueuedUpdates() {
        return queuedUpdates.get();
    }

    public long getProcessedUpdates() {
        return processedUpdates.get();
    }

    public long getBatchesProcessed() {
        return batchesProcessed.get();
    }

    public void clearQueue() {
        taskQueue.clear();
    }
}
