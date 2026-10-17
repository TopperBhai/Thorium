package dev.hyperionfps.fabric;

import java.util.concurrent.atomic.AtomicBoolean;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.core.ThreadGovernor;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.memory.IdleJanitor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Advanced Fabric API Integration Engine for Thorium.
 *
 * <p>Leverages Fabric API sub-modules for deep client integration:</p>
 * <ul>
 *   <li><b>fabric-key-binding-api-v1:</b> Native controls menu configuration.</li>
 *   <li><b>fabric-rendering-v1:</b> Real-time Nuclear Performance HUD with frametime & culling stats.</li>
 *   <li><b>fabric-lifecycle-events-v1:</b> Chunk eviction, zero-freeze dimension switching, and memory trimming.</li>
 * </ul>
 */
public final class ThoriumFabricHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/FabricAPI");
    private static final String KEY_CATEGORY = "key.category.thorium";

    private static KeyMapping toggleExtremeFpsKey;
    private static KeyMapping toggleNuclearHudKey;
    private static KeyMapping openThoriumGuiKey;

    private static int tickCounter = 0;

    private ThoriumFabricHooks() {
    }

    /**
     * Called at client initialization to register all Fabric API hooks.
     */
    public static void init() {
        LOGGER.info("Initializing Thorium Advanced Fabric API Subsystems...");

        registerKeyBindings();
        registerHudOverlay();
        registerLifecycleEvents();

        LOGGER.info("Thorium Fabric API Integration complete: Keybindings, Nuclear HUD & Chunk Lifecycles online!");
    }

    private static void registerKeyBindings() {
        toggleExtremeFpsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.thorium.extreme_fps",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KEY_CATEGORY
        ));

        toggleNuclearHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.thorium.nuclear_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KEY_CATEGORY
        ));

        openThoriumGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.thorium.settings_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KEY_CATEGORY
        ));
    }

    private static void registerHudOverlay() {
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            if (!ThoriumGuiManager.isHudEnabled()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen())) {
                return; // Suppress when any screen (like Settings/Chat/Inventory) or F3 is open
            }

            renderNuclearHud(guiGraphics, mc);
        });
    }

    private static void renderNuclearHud(GuiGraphics graphics, Minecraft mc) {
        Font font = mc.font;
        int fps = mc.getFps();
        float currentMs = ThoriumFrametimeTracker.getCurrentFrametimeMs();
        float avgMs = ThoriumFrametimeTracker.getAverageFrametimeMs();
        float maxMs = ThoriumFrametimeTracker.getMaxFrametimeMs();
        int spikes = ThoriumFrametimeTracker.getTotalStutterSpikes();
        float score = ThoriumFrametimeTracker.getSmoothnessScore();

        Runtime runtime = Runtime.getRuntime();
        long usedMemMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        int startX = 8;
        int startY = 8;
        int boxWidth = 246;
        int boxHeight = 65;

        // 1. Sleek semi-translucent dark frosted glass backdrop
        graphics.fill(startX, startY, startX + boxWidth, startY + boxHeight, 0x72080E14);

        // 2. Ultra-subtle, elegant 1px glass border
        graphics.fill(startX, startY, startX + boxWidth, startY + 1, 0x2AFFFFFF);
        graphics.fill(startX, startY + boxHeight - 1, startX + boxWidth, startY + boxHeight, 0x2AFFFFFF);
        graphics.fill(startX, startY + 1, startX + 1, startY + boxHeight, 0x2AFFFFFF);
        graphics.fill(startX + boxWidth - 1, startY, startX + boxWidth, startY + boxHeight, 0x2AFFFFFF);

        // 3. Header Row: Brand badge, Real-time FPS, Frametime, and Smoothness score
        String fpsText = "§bTHORIUM §f§l" + fps + " §r§7FPS §8(" + String.format("%.1f", currentMs) + "ms)";
        String smoothText = (score >= 95.0f ? "§a" : (score >= 80.0f ? "§e" : "§c")) + String.format("%.0f%%", score) + " Smooth";

        graphics.drawString(font, fpsText, startX + 6, startY + 4, 0xFFFFFFFF, false);
        int smoothW = font.width(smoothText);
        graphics.drawString(font, smoothText, startX + boxWidth - smoothW - 6, startY + 4, 0xFFFFFFFF, false);

        // -------------------------------------------------------------
        // 4. Clean Frametime Sparkline (Zero-Banding Design)
        // -------------------------------------------------------------
        int graphX = startX + 6;
        int graphY = startY + 17;
        int graphWidth = 234; // 117 bars * 2px
        int graphHeight = 27;
        int graphBottom = graphY + graphHeight;

        // Clean subtle dark glass recess (no harsh borders or horizontal stripes)
        graphics.fill(graphX, graphY, graphX + graphWidth, graphBottom, 0x30000000);

        // Single faint 60 FPS reference guide line (16.6ms)
        int y16ms = graphBottom - Math.min(graphHeight - 2, (int) (16.6f * 1.25f));
        graphics.fill(graphX, y16ms, graphX + graphWidth, y16ms + 1, 0x12FFFFFF);

        // Draw 117 Frametime Bars (2px each = 234px total width)
        for (int i = 0; i < 117; i++) {
            float sampleMs = ThoriumFrametimeTracker.getSampleMs(116 - i);
            if (sampleMs <= 0.0f) {
                continue;
            }

            int barHeight = Math.clamp((int) (sampleMs * 1.25f), 1, graphHeight - 2);
            int barColor;
            if (sampleMs <= 8.3f) {
                barColor = 0xFF00E5FF; // Aqua / Cyan: Butter smooth > 120 FPS
            } else if (sampleMs <= 16.6f) {
                barColor = 0xFF00E676; // Mint Green: Normal smooth 60-120 FPS
            } else if (sampleMs <= 33.3f) {
                barColor = 0xFFFFB300; // Amber Gold: Dropped frame 30-60 FPS
            } else {
                barColor = 0xFFFF1744; // Crimson Red: Micro-stutter spike caught!
            }

            int barX = graphX + (i * 2);
            graphics.fill(barX, graphBottom - barHeight, barX + 2, graphBottom, barColor);
        }

        // -------------------------------------------------------------
        // 5. Bottom Pro-Tier Telemetry Line (Comfortable Margins)
        // -------------------------------------------------------------
        String statsLine = "§7Avg: §f" + String.format("%.1f", avgMs) + "ms §8• §7Max: "
                + (maxMs > 16.6f ? "§c" : "§7") + String.format("%.1f", maxMs) + "ms §8• §7Spikes: "
                + (spikes == 0 ? "§a0" : "§c" + spikes) + " §8• §7RAM: §e" + usedMemMiB + "MB";

        graphics.drawString(font, statsLine, startX + 6, graphBottom + 5, 0xFFCFD8DC, false);
    }

    private static void registerLifecycleEvents() {
        // Tick listener for Keybind consumption and background governor maintenance
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleNuclearHudKey != null && toggleNuclearHudKey.consumeClick()) {
                boolean newState = !ThoriumGuiManager.isHudEnabled();
                ThoriumGuiManager.setHudEnabled(newState);
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§2[§aThorium§2] Nuclear Performance HUD: " + (newState ? "§aON" : "§cOFF")),
                            true
                    );
                }
            }

            while (toggleExtremeFpsKey != null && toggleExtremeFpsKey.consumeClick()) {
                Component notification = ThoriumGuiManager.toggleExtremeFpsMode();
                if (client.player != null) {
                    client.player.displayClientMessage(notification, true);
                }
            }

            while (openThoriumGuiKey != null && openThoriumGuiKey.consumeClick()) {
                client.setScreen(new dev.hyperionfps.gui.ThoriumVideoSettingsScreen(client.screen));
            }

            tickCounter++;
            if (tickCounter % 100 == 0) {
                ThreadGovernor.boostActiveMinecraftThreads();
            }
        });

        // Instant cache eviction on chunk unload to prevent memory leaks
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            // Evict chunk-specific occlusion and pooled data
            AsyncOcclusionCuller.evictChunk(chunk.getPos().x, chunk.getPos().z);
        });

        // Zero-freeze dimension switching and world transition memory sweep
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            LOGGER.info("Dimension transition detected: executing zero-freeze cache & direct memory cleanup");
            AsyncOcclusionCuller.clearCache();
            IdleJanitor.sweepOnce(0L); // Immediately purge idle direct off-heap buffers
        });
    }

    public static boolean isHudEnabled() {
        return ThoriumGuiManager.isHudEnabled();
    }

    public static void setHudEnabled(boolean enabled) {
        ThoriumGuiManager.setHudEnabled(enabled);
    }
}
