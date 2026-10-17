package dev.hyperionfps.core;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telemetry kill-switch consulted by the telemetry mixin.
 *
 * <p>The vanilla client only needs a {@code false} answer at
 * {@code ClientTelemetryManager.createEventSender()} to route every telemetry
 * path through its own built-in {@code DISABLED} sender. HyperionFPS never has
 * to cancel individual events or tear down the telemetry manager itself:
 * answering "not allowed" at that single choke point stops session creation,
 * batching threads, network traffic and log noise in one step. The policy
 * lives in this class so the mixin stays a one-liner and the decision is
 * unit-testable on a plain JVM.</p>
 */
public final class TelemetryGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/Telemetry");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private TelemetryGate() {
    }

    /** Whether vanilla telemetry may run. HyperionFPS always answers no. */
    public static boolean allowed() {
        if (REPORTED.compareAndSet(false, true)) {
            LOGGER.info("Telemetry suppressed: session creation short-circuited to the game's own DISABLED event sender");
        }
        return false;
    }
}
