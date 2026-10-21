package dev.hyperionfps.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.hyperionfps.config.ThoriumConfig;
import dev.hyperionfps.config.ThoriumPresets;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import dev.hyperionfps.texture.ThoriumTextureThrottler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * Premium Sodium-Grade Thorium Video & Performance Architecture Console (Minecraft 1.21.1 Edition).
 */
public class ThoriumVideoSettingsScreen extends Screen {

    public enum Tab {
        GENERAL("General"),
        PERFORMANCE("Performance"),
        ADVANCED("Advanced");

        private final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static volatile boolean openingVanilla = false;
    private static Tab currentTab = Tab.GENERAL;
    private static String activePreset = ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, "BALANCED");

    /** Whether this Minecraft version has a Thorium hook in the chunk upload path. */
    private static final boolean CHUNK_UPLOAD_PACER_SUPPORTED = true;

    private final Screen lastScreen;
    private final List<AbstractWidget> tabWidgets = new ArrayList<>();
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private SettingRowButton hoveredRow = null;

    /** Top edge of the scrollable settings list. */
    private int contentTop = 44;

    /** Bottom edge of the scrollable settings list - the footer bar sits below it. */
    private int contentBottom = 200;

    /** Current vertical scroll offset of the settings list, in pixels. */
    private int scrollOffset = 0;

    public ThoriumVideoSettingsScreen(Screen lastScreen) {
        super(Component.literal("Thorium Settings"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        tabWidgets.clear();
        contentWidgets.clear();
        hoveredRow = null;
        activePreset = ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, activePreset);

        int marginX = 24;
        int topY = 12;

        // -------------------------------------------------------------
        // 1. Top Navigation Bar: Tabs (Left) & Preset Switcher (Right)
        // -------------------------------------------------------------
        Tab[] allTabs = Tab.values();
        int tabX = marginX;
        int tabH = 20;

        for (Tab tab : allTabs) {
            int tabW = this.font.width(tab.getDisplayName()) + 20;
            TabButton tabBtn = new TabButton(tabX, topY, tabW, tabH, tab, b -> switchTab(tab));
            this.addRenderableWidget(tabBtn);
            tabWidgets.add(tabBtn);
            tabX += tabW + 6;
        }

        // Preset buttons on the top right
        int presetW = 76;
        int presetH = 20;
        int presetGap = 6;
        int presetRight = this.width - marginX;

        addRenderableWidget(new PresetButton(presetRight - presetW, topY, presetW, presetH, "NUCLEAR", "NUCLEAR", 0xFFFF1744));
        addRenderableWidget(new PresetButton(presetRight - (presetW * 2) - presetGap, topY, presetW, presetH, "BALANCED", "BALANCED", 0xFF00E5FF));
        addRenderableWidget(new PresetButton(presetRight - (presetW * 3) - (presetGap * 2), topY, presetW, presetH, "POTATO", "POTATO", 0xFFFFB300));

        // -------------------------------------------------------------
        // 2. Main Body: Settings Rows
        // -------------------------------------------------------------
        // The rows live in a band between the header and the footer bar. When the
        // window is too short for the whole list, the list scrolls inside that band
        // instead of spilling over the footer.
        this.contentTop = ThoriumSettingsLayout.contentTop();
        this.contentBottom = ThoriumSettingsLayout.contentBottom(this.height);
        buildTabContent();
        applyScrollOffset();

        // -------------------------------------------------------------
        // 3. Footer Bar: Vanilla Settings (Left) & Done (Right)
        // -------------------------------------------------------------
        int footerY = this.height - 28;
        int footerBtnH = 20;

        addRenderableWidget(new ModernFooterButton(
                marginX, footerY, 150, footerBtnH,
                "Vanilla Video Settings...",
                0xFF8899A6,
                b -> {
                    if (this.minecraft != null) {
                        openingVanilla = true;
                        this.minecraft.setScreen(new VideoSettingsScreen(this, this.minecraft, this.minecraft.options));
                    }
                }
        ));

        addRenderableWidget(new ModernFooterButton(
                this.width - marginX - 90, footerY, 90, footerBtnH,
                "Done",
                0xFF00E5FF,
                b -> this.onClose()
        ));
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        for (AbstractWidget w : contentWidgets) {
            this.removeWidget(w);
        }
        contentWidgets.clear();
        hoveredRow = null;
        activePreset = ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, activePreset);
        this.scrollOffset = 0;
        buildTabContent();
        applyScrollOffset();
    }

    private void buildTabContent() {
        int marginX = listLeft();
        int listWidth = listRight() - marginX;
        int rowH = ThoriumSettingsLayout.ROW_HEIGHT;
        int rowGap = ThoriumSettingsLayout.ROW_GAP;
        int currentY = this.contentTop;

        // The rows themselves live in the shared catalogue, so every version node
        // shows the same switches with the same behaviour. Only the widget type and
        // the rendering differ between the legacy and the modern era.
        List<ThoriumSettingRows.Spec> rows = switch (currentTab) {
            case GENERAL -> ThoriumSettingRows.generalRows(() -> ThoriumDiscordScreen.openInvite());
            case PERFORMANCE -> ThoriumSettingRows.performanceRows(CHUNK_UPLOAD_PACER_SUPPORTED);
            case ADVANCED -> ThoriumSettingRows.advancedRows();
        };

        for (ThoriumSettingRows.Spec spec : rows) {
            addSettingRow(new SettingRowButton(marginX, currentY, listWidth, rowH,
                    spec.title(),
                    spec.status(),
                    mapControlType(spec.controlType()),
                    spec.enabled(),
                    b -> spec.onClick().run(s -> b.setTempStatus(s)),
                    spec.description(),
                    spec.impactBadge()));
            currentY += rowH + rowGap;
        }
    }

    /** Maps the shared control type onto this version's widget enum. */
    private static SettingControlType mapControlType(ThoriumSettingRows.ControlType type) {
        return switch (type) {
            case CHECKBOX -> SettingControlType.CHECKBOX;
            case CYCLE -> SettingControlType.CYCLE;
            case ACTION -> SettingControlType.ACTION;
        };
    }

    /**
     * Adds a settings row to the scrollable list.
     *
     * <p>The row is added as a plain widget instead of a renderable widget: the rows
     * are drawn by hand inside the clipped content band, so a row can never be drawn
     * over the footer bar.</p>
     */
    private void addSettingRow(SettingRowButton button) {
        this.addWidget(button);
        this.contentWidgets.add(button);
    }

    /**
     * Positions the settings rows for the current scroll offset.
     *
     * <p>Rows that scrolled completely out of the content band are hidden, so they
     * are neither drawn nor clickable. A row that is only partly inside the band is
     * clipped by the scissor in the render pass.</p>
     */
    private void applyScrollOffset() {
        this.scrollOffset = ThoriumSettingsLayout.clampScrollOffset(
                this.scrollOffset, contentWidgets.size(), this.height);
        for (AbstractWidget w : contentWidgets) {
            if (w instanceof SettingRowButton row) {
                int y = row.baseY - this.scrollOffset;
                row.setY(y);
                row.bandTop = this.contentTop;
                row.bandBottom = this.contentBottom;
                row.visible = ThoriumSettingsLayout.isRowVisible(y, this.height);
            }
        }
    }

    /** Scrolls the settings list by the given number of pixels (positive scrolls up). */
    private void scrollBy(int pixels) {
        this.scrollOffset += pixels;
        applyScrollOffset();
    }

    /** Left edge of the settings list column. */
    private int listLeft() {
        return 24;
    }

    /** Right edge of the settings list column. */
    private int listRight() {
        return listLeft() + Math.min(360, (this.width / 2) + 20);
    }

    private void applyPreset(String preset) {
        // The preset logic is shared: all five version nodes apply the same profile.
        ThoriumPresets.apply(preset);
        activePreset = ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, activePreset);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 1. Semi-translucent full-screen backdrop (Allows live game world to be visible behind UI)
        graphics.fill(0, 0, this.width, this.height, 0x7E090E14);

        // Header separator line
        graphics.fill(20, 36, this.width - 20, 37, 0x22FFFFFF);

        // 2. Identify currently hovered row for the dynamic right panel
        SettingRowButton activeHover = null;
        for (AbstractWidget w : contentWidgets) {
            if (w instanceof SettingRowButton rowBtn && rowBtn.isHoveredOrFocused()) {
                activeHover = rowBtn;
                break;
            }
        }
        this.hoveredRow = activeHover;

        // 3. Render Right-Hand Detail & Telemetry Panel (Sodium Style)
        int marginX = 24;
        int listWidth = Math.min(360, (this.width / 2) + 20);
        int panelX = marginX + listWidth + 18;
        int panelY = 44;
        int panelW = this.width - marginX - panelX;
        int panelH = this.height - 82;

        if (panelW > 140) {
            // Subtle translucent glass card
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x3D0B1218);
            graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0x1AFFFFFF);
            graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x1AFFFFFF);
            graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x1AFFFFFF);
            graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0x1AFFFFFF);

            if (this.hoveredRow != null) {
                // Show focused option details
                graphics.drawString(this.font, this.hoveredRow.title, panelX + 12, panelY + 12, 0xFF00E5FF, false);
                graphics.drawString(this.font, this.hoveredRow.impactBadge, panelX + 12, panelY + 26, 0xFF00E676, false);

                // Divider inside card
                graphics.fill(panelX + 12, panelY + 38, panelX + panelW - 12, panelY + 39, 0x1FFFFFFF);

                // Wrapped description text
                List<String> wrappedLines = wrapText(this.hoveredRow.description, panelW - 24);
                int textY = panelY + 46;
                for (String line : wrappedLines) {
                    graphics.drawString(this.font, line, panelX + 12, textY, 0xFFB0BEC5, false);
                    textY += 11;
                }
            } else {
                // Show Live Engine Telemetry when no option is hovered
                graphics.drawString(this.font, "Thorium Engine Telemetry", panelX + 12, panelY + 12, 0xFFFFFFFF, false);
                graphics.drawString(this.font, "§7Real-Time Zero-Stutter Performance", panelX + 12, panelY + 25, 0xFF8899A6, false);

                graphics.fill(panelX + 12, panelY + 38, panelX + panelW - 12, panelY + 39, 0x1FFFFFFF);

                Minecraft mc = Minecraft.getInstance();
                int fps = mc.getFps();
                float curMs = ThoriumFrametimeTracker.getCurrentFrametimeMs();
                float score = ThoriumFrametimeTracker.getSmoothnessScore();
                int spikes = ThoriumFrametimeTracker.getTotalStutterSpikes();

                graphics.drawString(this.font, "Current FPS: §a" + fps + " FPS", panelX + 12, panelY + 48, 0xFFE0E0E0, false);
                graphics.drawString(this.font, "Frametime: §b" + String.format("%.1f", curMs) + " ms", panelX + 12, panelY + 62, 0xFFE0E0E0, false);
                graphics.drawString(this.font, "Smoothness: §a" + String.format("%.1f%%", score), panelX + 12, panelY + 76, 0xFFE0E0E0, false);
                graphics.drawString(this.font, "Stutter Spikes: " + (spikes == 0 ? "§a0 (Clean)" : "§c" + spikes), panelX + 12, panelY + 90, 0xFFE0E0E0, false);

                graphics.fill(panelX + 12, panelY + 106, panelX + panelW - 12, panelY + 107, 0x14FFFFFF);

                long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                graphics.drawString(this.font, "JVM Heap: §e" + usedMb + "§7/" + maxMb + " MB", panelX + 12, panelY + 114, 0xFFB0BEC5, false);
                graphics.drawString(this.font, "Throttled Sprites: §a" + ThoriumTextureThrottler.getSkippedUploads(), panelX + 12, panelY + 128, 0xFFB0BEC5, false);
                graphics.drawString(this.font, "BVH Cull Clusters: §a" + ThoriumBvhCuller.getClusterCulledCount(), panelX + 12, panelY + 142, 0xFFB0BEC5, false);
            }
        }

        // 4. Render Child Widgets (Tabs, Buttons, Presets, Footer)
        super.render(graphics, mouseX, mouseY, partialTicks);

        // 5. Render the settings rows inside the clipped content band. The scissor
        //    keeps a partially scrolled row from being drawn over the footer bar.
        graphics.enableScissor(listLeft(), this.contentTop, listRight(), this.contentBottom);
        for (AbstractWidget w : contentWidgets) {
            w.render(graphics, mouseX, mouseY, partialTicks);
        }
        graphics.disableScissor();
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String w : words) {
            String test = current.isEmpty() ? w : current + " " + w;
            if (this.font.width(test) <= maxWidth) {
                current = new StringBuilder(test);
            } else {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                }
                current = new StringBuilder(w);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * Scrolls the settings list with the mouse wheel.
     *
     * <p>Only the list column reacts to the wheel, and only while the cursor is over
     * it, so the wheel never steals an event from the tabs, the presets or the
     * footer bar. Vanilla reports a wheel-up event with a positive delta, which
     * scrolls the list up - the same direction the advancements screen uses.</p>
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D
                && mouseX >= listLeft() && mouseX <= listRight()
                && mouseY >= this.contentTop && mouseY <= this.contentBottom) {
            scrollBy((int) Math.round(scrollY * 20.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        ThoriumConfig.get().saveNow();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.lastScreen);
        }
    }

    // =========================================================================
    // Modern Sodium-Inspired Setting Row & Controls
    // =========================================================================

    public enum SettingControlType {
        CHECKBOX,
        CYCLE,
        ACTION
    }

    private static class SettingRowButton extends Button {
        final String title;
        final Supplier<String> statusSupplier;
        final SettingControlType controlType;
        final Supplier<Boolean> enabledSupplier;
        final Consumer<SettingRowButton> onClickAction;
        final String description;
        final String impactBadge;
        /** Y position of the row in the unscrolled list - the scroll offset is applied to it. */
        final int baseY;
        /** Top edge of the visible content band - a click above it is ignored. */
        int bandTop = 0;
        /** Bottom edge of the visible content band - a click below it is ignored. */
        int bandBottom = Integer.MAX_VALUE;
        private String tempStatus = null;

        public SettingRowButton(int x, int y, int width, int height,
                                String title, Supplier<String> statusSupplier,
                                SettingControlType controlType, Supplier<Boolean> enabledSupplier,
                                Consumer<SettingRowButton> onClickAction,
                                String description, String impactBadge) {
            super(x, y, width, height, Component.empty(), b -> {
                onClickAction.accept((SettingRowButton) b);
                activePreset = ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, activePreset);
                ThoriumConfig.get().saveNow();
            }, DEFAULT_NARRATION);
            this.title = title;
            this.statusSupplier = statusSupplier;
            this.controlType = controlType;
            this.enabledSupplier = enabledSupplier;
            this.onClickAction = onClickAction;
            this.description = description;
            this.impactBadge = impactBadge;
            this.baseY = y;
        }

        public void setTempStatus(String status) {
            this.tempStatus = status;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // A row that is only partly inside the content band must not react to a
            // click in its clipped part: that part of the screen belongs to the
            // footer bar.
            return mouseY >= this.bandTop && mouseY < this.bandBottom && super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean hovered = this.isHoveredOrFocused();

            // Subtle hover strip across the entire row
            if (hovered) {
                graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x1FFFFFFF);
            }

            int textY = this.getY() + (this.height - 8) / 2;

            // Option Name (Left)
            int titleColor = hovered ? 0xFFFFFFFF : 0xFFCFD8DC;
            graphics.drawString(Minecraft.getInstance().font, this.title, this.getX() + 6, textY, titleColor, false);

            // Control Element (Right)
            boolean isEnabled = enabledSupplier.get();
            String status = tempStatus != null ? tempStatus : statusSupplier.get();

            if (this.controlType == SettingControlType.CHECKBOX) {
                // Sleek Sodium-style square checkbox
                int boxSize = 14;
                int boxX = this.getX() + this.width - boxSize - 6;
                int boxY = this.getY() + (this.height - boxSize) / 2;

                if (isEnabled) {
                    // Active: Cyan/Emerald filled box with checkmark
                    graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x4400E5FF);
                    graphics.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0xFF00E5FF);
                    graphics.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0xFF00E5FF);
                    graphics.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0xFF00E5FF);
                    graphics.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0xFF00E5FF);

                    // Checkmark symbol
                    int checkX = boxX + 3;
                    int checkY = boxY + 3;
                    graphics.drawString(Minecraft.getInstance().font, "✔", checkX, checkY, 0xFF00E5FF, false);
                } else {
                    // Inactive: Subtle dark box with dim border
                    graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x33000000);
                    graphics.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0x44FFFFFF);
                    graphics.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0x44FFFFFF);
                    graphics.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0x44FFFFFF);
                    graphics.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0x44FFFFFF);
                }
            } else {
                // Cycle / Action Pill Button
                int badgeW = Minecraft.getInstance().font.width(status) + 12;
                int badgeH = 16;
                int badgeX = this.getX() + this.width - badgeW - 6;
                int badgeY = this.getY() + (this.height - badgeH) / 2;

                int pillBg = hovered ? 0x4400E5FF : 0x22FFFFFF;
                int pillBorder = isEnabled ? 0xFF00E5FF : 0x44FFFFFF;

                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, pillBg);
                graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, pillBorder);
                graphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, pillBorder);
                graphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, pillBorder);
                graphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, pillBorder);

                int strX = badgeX + (badgeW - Minecraft.getInstance().font.width(status)) / 2;
                int strY = badgeY + (badgeH - 8) / 2;
                graphics.drawString(Minecraft.getInstance().font, status, strX, strY, isEnabled ? 0xFFFFFFFF : 0xFF90A4AE, false);
            }
        }
    }

    /**
     * Sleek tab button with glowing bottom underline when active.
     */
    private class TabButton extends Button {
        private final Tab tab;

        public TabButton(int x, int y, int width, int height, Tab tab, OnPress onPress) {
            super(x, y, width, height, Component.literal(tab.getDisplayName()), onPress, DEFAULT_NARRATION);
            this.tab = tab;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean active = (this.tab == currentTab);
            boolean hovered = this.isHoveredOrFocused();

            if (active) {
                // Glowing cyan underline on active tab
                graphics.fill(this.getX(), this.getY() + this.height - 2, this.getX() + this.width, this.getY() + this.height, 0xFF00E5FF);
            } else if (hovered) {
                graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, 0x44FFFFFF);
            }

            int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFE0E0E0 : 0xFF8899A6);
            int textW = Minecraft.getInstance().font.width(this.tab.getDisplayName());
            int textX = this.getX() + (this.width - textW) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            graphics.drawString(Minecraft.getInstance().font, this.tab.getDisplayName(), textX, textY, textColor, false);
        }
    }

    /**
     * Preset Profile Selector Button (Potato Gold, Balanced Aqua, Nuclear Red).
     */
    private class PresetButton extends Button {
        private final String presetName;
        private final String presetKey;
        private final int accentColor;

        public PresetButton(int x, int y, int width, int height, String presetName, String presetKey, int accentColor) {
            super(x, y, width, height, Component.empty(), b -> applyPreset(presetKey), DEFAULT_NARRATION);
            this.presetName = presetName;
            this.presetKey = presetKey;
            this.accentColor = accentColor;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean active = activePreset.equals(this.presetKey);
            boolean hovered = this.isHoveredOrFocused();

            // Background glow
            int bg = active ? (0x33000000 | (accentColor & 0x00FFFFFF)) : (hovered ? 0x22FFFFFF : 0x14000000);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);

            // Border
            int border = active ? accentColor : (hovered ? 0x66FFFFFF : 0x22FFFFFF);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, border);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, border);

            String text = (active ? "★ " : "") + presetName;
            int textW = Minecraft.getInstance().font.width(text);
            int textX = this.getX() + (this.width - textW) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            graphics.drawString(Minecraft.getInstance().font, text, textX, textY, active ? accentColor : 0xFF8899A6, false);
        }
    }

    /**
     * Minimalist bottom footer button.
     */
    private static class ModernFooterButton extends Button {
        private final int accentColor;
        private final String label;

        public ModernFooterButton(int x, int y, int width, int height, String label, int accentColor, OnPress onPress) {
            super(x, y, width, height, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.label = label;
            this.accentColor = accentColor;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean hovered = this.isHoveredOrFocused();

            int bg = hovered ? 0x33FFFFFF : 0x1A000000;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);

            int border = hovered ? accentColor : 0x28FFFFFF;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, border);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, border);

            int textW = Minecraft.getInstance().font.width(this.label);
            int textX = this.getX() + (this.width - textW) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            graphics.drawString(Minecraft.getInstance().font, this.label, textX, textY, hovered ? 0xFFFFFFFF : 0xFFB0BEC5, false);
        }
    }
}
