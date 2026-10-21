package dev.hyperionfps.core;

import java.util.ArrayList;
import java.util.List;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.audio.ThoriumSoundGovernor;
import dev.hyperionfps.batch.ThoriumImmediateBatcher;
import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.OcclusionGrid;
import dev.hyperionfps.culling.SectionCullingEngine;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.frame.ThoriumFrameContext;
import dev.hyperionfps.frame.ThoriumFramePacer;
import dev.hyperionfps.input.ThoriumInputPacer;
import dev.hyperionfps.instancing.EntityInstancer;
import dev.hyperionfps.lighting.AsyncLightEngine;
import dev.hyperionfps.particle.ParticleBatchEngine;
import dev.hyperionfps.particle.ThoriumParticleBudget;
import dev.hyperionfps.render.batching.HudBatcher;
import dev.hyperionfps.render.state.GlStateTracker;
import dev.hyperionfps.texture.ThoriumTextureThrottler;
import dev.hyperionfps.version.Era;
import dev.hyperionfps.version.GameVersionDetector;

/**
 * Reports what Thorium is <em>actually</em> doing on this session.
 *
 * <p>The startup log used to print a hardcoded list of subsystem states
 * ("culling=active (sections+entities)", "instancing=ready", "async-lighting=active")
 * regardless of whether the subsystem was wired into the running game. Three of those
 * claims were false on every version, and one of them - the Fabric API integration - was
 * even printed as integrated while the hook had failed to load.</p>
 *
 * <p>This report replaces that string with values read from the live subsystems. It
 * distinguishes five states:</p>
 *
 * <ul>
 *   <li>{@code ACTIVE} - wired and measurably doing work.</li>
 *   <li>{@code IDLE} - wired and enabled, but nothing has exercised it yet.</li>
 *   <li>{@code DISABLED} - the user turned it off.</li>
 *   <li>{@code UNWIRED} - implemented and exposed in the console, but no call site
 *       invokes it. These are the fake toggles.</li>
 *   <li>{@code UNSUPPORTED} - not available on this Minecraft version.</li>
 * </ul>
 */
public final class ThoriumSubsystemReport {

    /** Lifecycle state of a single subsystem. */
    public enum Status {
        ACTIVE,
        IDLE,
        DISABLED,
        UNWIRED,
        UNSUPPORTED
    }

    /** One line of the report. */
    public record Entry(String feature, Status status, String detail) {
    }

    private ThoriumSubsystemReport() {
    }

    /** Builds the report from the live state of every subsystem. */
    public static List<Entry> collect() {
        List<Entry> entries = new ArrayList<>();
        boolean legacyEra = GameVersionDetector.detect().era() == Era.LEGACY;
        long clientFrames = ThoriumFrameContext.clientFrameCount();
        long worldFrames = ThoriumFrameContext.worldFrameCount();

        entries.add(new Entry("frame-context",
                clientFrames > 0 ? Status.ACTIVE : Status.UNWIRED,
                clientFrames + " client frames observed"));

        entries.add(new Entry("camera-context",
                worldFrames > 0 ? Status.ACTIVE : Status.IDLE,
                worldFrames + " world frames observed"));

        entries.add(new Entry("frame-pacer",
                !ThoriumFramePacer.isEnabled() ? Status.DISABLED
                        : (ThoriumFramePacer.getTargetFps() > 0 ? Status.ACTIVE : Status.IDLE),
                ThoriumFramePacer.getTargetFps() > 0
                        ? ThoriumFramePacer.getTargetFps() + " FPS target"
                        : "enabled, uncapped"));

        entries.add(new Entry("entity-culling",
                !AsyncOcclusionCuller.isEnabled() ? Status.DISABLED
                        : (worldFrames > 0 ? Status.ACTIVE : Status.UNWIRED),
                "distance + frustum + occlusion, scale "
                        + String.format(java.util.Locale.ROOT, "%.2f", AsyncOcclusionCuller.getDynamicDistanceScale())));

        entries.add(new Entry("block-entity-culling",
                !AsyncOcclusionCuller.isEnabled() ? Status.DISABLED
                        : (worldFrames > 0 ? Status.ACTIVE : Status.UNWIRED),
                "distance + frustum + raycast"));

        entries.add(new Entry("particle-budget",
                !ThoriumParticleBudget.isEnabled() ? Status.DISABLED
                        : (ThoriumParticleBudget.getAdmitted() + ThoriumParticleBudget.getRejected() > 0
                                ? Status.ACTIVE : Status.IDLE),
                ThoriumParticleBudget.getAdmitted() + " admitted, "
                        + ThoriumParticleBudget.getRejected() + " rejected"));

        entries.add(new Entry("bvh-cluster-culling",
                !ThoriumBvhCuller.isEnabled() ? Status.DISABLED
                        : (ThoriumBvhCuller.getClusterCulledCount() > 0 ? Status.ACTIVE : Status.IDLE),
                ThoriumBvhCuller.getClusterCulledCount() + " clusters culled"));

        entries.add(new Entry("occlusion-grid",
                !OcclusionGrid.isEnabled() ? Status.DISABLED
                        : (OcclusionGrid.getOcclusionChecks() > 0 ? Status.ACTIVE : Status.IDLE),
                OcclusionGrid.getOcclusionChecks() + " checks, "
                        + OcclusionGrid.getSectionsOccludedCount() + " sections occluded"));

        entries.add(new Entry("entity-animation-lod",
                !ThoriumEntityLodThrottler.isEnabled() ? Status.DISABLED : Status.IDLE,
                "distance based animation throttling"));

        entries.add(new Entry("sound-governor",
                !ThoriumSoundGovernor.isEnabled() ? Status.DISABLED
                        : (ThoriumSoundGovernor.getSoundsSuppressed() > 0 ? Status.ACTIVE : Status.IDLE),
                ThoriumSoundGovernor.getSoundsSuppressed() + " sounds suppressed"));

        entries.add(new Entry("texture-throttler",
                !ThoriumTextureThrottler.isEnabled() ? Status.DISABLED
                        : (ThoriumTextureThrottler.getSkippedUploads() > 0 ? Status.ACTIVE : Status.IDLE),
                "every " + ThoriumTextureThrottler.getThrottleDivider() + " tick, "
                        + ThoriumTextureThrottler.getSkippedUploads() + " uploads skipped"));

        entries.add(new Entry("hud-batching",
                !HudBatcher.isEnabled() ? Status.DISABLED
                        : (HudBatcher.getDrawCallsSaved() > 0 ? Status.ACTIVE : Status.IDLE),
                HudBatcher.getDrawCallsSaved() + " draw calls saved"));

        entries.add(new Entry("adaptive-render-distance",
                !AdaptiveDistanceController.getInstance().isEnabled() ? Status.DISABLED
                        : (AdaptiveDistanceController.getInstance().getAdjustmentsMade() > 0
                                ? Status.ACTIVE : Status.IDLE),
                "target " + AdaptiveDistanceController.getInstance().getCurrentDistance()
                        + " chunks, smoothed "
                        + (int) AdaptiveDistanceController.getInstance().getSmoothedFps() + " FPS"));

        entries.add(new Entry("chunk-upload-pacer",
                !legacyEra ? Status.UNSUPPORTED
                        : (!ThoriumChunkUploadPacer.isEnabled() ? Status.DISABLED
                                : (ThoriumChunkUploadPacer.getTotalUploads() > 0 ? Status.ACTIVE : Status.IDLE)),
                legacyEra
                        ? ThoriumChunkUploadPacer.getTotalUploads() + " uploads, "
                                + ThoriumChunkUploadPacer.getYieldedFrames() + " frames yielded"
                        : "the 26.x upload pipeline has no Thorium hook"));

        // --- Implemented, exposed in the console, but never invoked. -------------
        entries.add(new Entry("section-culling", Status.UNWIRED,
                "SectionCullingEngine.shouldRenderSection has no call site"));
        entries.add(new Entry("particle-sheet-batching", Status.UNWIRED,
                "ParticleBatchEngine has no call site"));
        entries.add(new Entry("gpu-entity-instancing", Status.UNWIRED,
                "EntityInstancer has no call site"));
        entries.add(new Entry("async-light-propagation", Status.UNWIRED,
                "the worker runs but never receives work"));
        entries.add(new Entry("gl-state-filter", Status.UNWIRED,
                "GlStateTracker.shouldBindTexture has no call site"));
        entries.add(new Entry("immediate-2d-batching", Status.UNWIRED,
                "the flush runs but shouldBatchDraw is never consulted"));
        entries.add(new Entry("input-pacing", Status.UNWIRED,
                "ThoriumInputPacer.recordPacedTurn has no call site"));

        // --- Headline features --------------------------------------------------
        // Each one reports itself as UNWIRED until its implementation lands, so the
        // console and the log never advertise a switch that cannot do anything.
        for (ThoriumFeatureToggles.Feature feature : ThoriumFeatureToggles.Feature.values()) {
            entries.add(new Entry(feature.reportName(),
                    !feature.isWired() ? Status.UNWIRED
                            : (!feature.isEnabled() ? Status.DISABLED : Status.IDLE),
                    !feature.isWired() ? "the implementation has not landed yet"
                            : (feature.isEnabled() ? "enabled" : "disabled by the player")));
        }

        entries.add(new Entry("weather-rendering",
                ThoriumFeatureToggles.getWeatherMode() == ThoriumFeatureToggles.WeatherMode.VANILLA
                        ? Status.IDLE : Status.ACTIVE,
                ThoriumFeatureToggles.getWeatherMode().name()));

        return entries;
    }

    /** Renders the report as an aligned, multi-line block. */
    public static String format() {
        List<Entry> entries = collect();
        int width = 0;
        for (Entry entry : entries) {
            width = Math.max(width, entry.feature().length());
        }
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append("  ").append(entry.feature());
            for (int i = entry.feature().length(); i < width; i++) {
                out.append(' ');
            }
            out.append("  ").append(entry.status().name());
            if (entry.detail() != null && !entry.detail().isEmpty()) {
                out.append(" - ").append(entry.detail());
            }
            out.append('\n');
        }
        return out.toString();
    }
}
