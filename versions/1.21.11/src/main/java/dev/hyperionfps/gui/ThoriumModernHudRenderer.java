package dev.hyperionfps.gui;

import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modern-era (1.21.11) Ultra-Clean Sodium-Grade HUD & Micro-Stutter Sparkline.
 */
public final class ThoriumModernHudRenderer {

    private ThoriumModernHudRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft mc) {
        if (mc.player == null || mc.screen != null || (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen())) {
            return;
        }

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

        graphics.nextStratum();

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
                + (spikes == 0 ? "§a0" : "§c" + spikes) + " §8• §7RAM: §e" + usedMemMiB + "MB" + ThoriumGuiManager.getEcoBadge();

        graphics.drawString(font, statsLine, startX + 6, graphBottom + 5, 0xFFCFD8DC, false);

        // The toast line: the Stutter Hunter explains a freeze here.
        String toast = ThoriumToastQueue.activeToast();
        if (!toast.isEmpty()) {
            graphics.drawString(font, toast, startX + 6, graphBottom + 17, 0xFFFFC107, false);
        }
    }
}
