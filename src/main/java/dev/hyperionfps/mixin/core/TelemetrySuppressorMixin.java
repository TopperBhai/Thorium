package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.core.TelemetryGate;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;

/**
 * Cuts all telemetry at its single choke point.
 *
 * <p>Both supported eras funnel every telemetry path through
 * {@code ClientTelemetryManager.createEventSender()}: the world-session
 * manager delegates to it, and inside it the session's enabled flag chooses
 * between a live sender and the built-in {@code DISABLED} one. Returning the
 * disabled sender from a HEAD injection therefore disables telemetry
 * wholesale - no session is ever created, no batching thread starts, nothing
 * is queued - while the rest of the class (log directory, close handling)
 * keeps working exactly as vanilla expects.</p>
 *
 * <p>Fails soft: {@code require = 0} turns a missed injection on an unforeseen
 * future version into a log warning, and the game simply keeps its vanilla
 * telemetry behavior.</p>
 */
@Mixin(ClientTelemetryManager.class)
public abstract class TelemetrySuppressorMixin {

    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$suppressTelemetry(CallbackInfoReturnable<TelemetryEventSender> callbackInfo) {
        if (!TelemetryGate.allowed()) {
            callbackInfo.setReturnValue(TelemetryEventSender.DISABLED);
        }
    }
}
