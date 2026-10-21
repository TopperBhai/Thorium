package dev.hyperionfps.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.discord.ThoriumDiscordInvite;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
/**
 * One-time community invite card, shown over the main menu on the first launch.
 *
 * <p>Legacy-era (1.21.x) variant: the classic renderer draws through
 * {@link GuiGraphics}.</p>
 *
 * <p>The card is offered exactly once. Both buttons close it, and closing it records
 * that it has been seen - a player who says "not now" is never asked again. The link
 * stays reachable from the settings console.</p>
 */
public class ThoriumDiscordScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/Discord");

    private static final int CARD_WIDTH = 340;
    private static final int CARD_HEIGHT = 138;
    private static final int PADDING = 16;

    private final Screen previousScreen;

    public ThoriumDiscordScreen(Screen previousScreen) {
        super(Component.literal("Join the Thorium Discord"));
        this.previousScreen = previousScreen;
    }

    /**
     * Opens the community invite in the player's browser.
     *
     * <p>Public so the settings console can offer the same link without duplicating
     * the era-specific browser hand-off.</p>
     */
    public static void openInvite() {
        try {
            Util.getPlatform().openUri(ThoriumDiscordInvite.INVITE_URL);
        } catch (Throwable failure) {
            LOGGER.warn("Could not open the community invite: {}", failure.toString());
        }
    }

    @Override
    protected void init() {
        int buttonW = 152;
        int buttonH = 20;
        int gap = 8;
        int rowW = buttonW * 2 + gap;
        int x = (this.width - rowW) / 2;
        int y = (this.height + CARD_HEIGHT) / 2 - buttonH - 12;

        this.addRenderableWidget(Button.builder(
                Component.literal("Join the Discord"),
                b -> {
                    openInvite();
                    onClose();
                })
                .bounds(x, y, buttonW, buttonH)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Not now"),
                b -> onClose())
                .bounds(x + buttonW + gap, y, buttonW, buttonH)
                .build());
    }

    @Override
    public void onClose() {
        ThoriumDiscordInvite.markShown();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Dim the main menu behind the card.
        graphics.fill(0, 0, this.width, this.height, 0xB0090E14);

        int cardX = (this.width - CARD_WIDTH) / 2;
        int cardY = (this.height - CARD_HEIGHT) / 2;

        // Frosted glass card.
        graphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0xE60B1218);
        graphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + 1, 0x33FFFFFF);
        graphics.fill(cardX, cardY + CARD_HEIGHT - 1, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0x33FFFFFF);
        graphics.fill(cardX, cardY, cardX + 1, cardY + CARD_HEIGHT, 0x33FFFFFF);
        graphics.fill(cardX + CARD_WIDTH - 1, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0x33FFFFFF);
        // Cyan accent bar on the left edge.
        graphics.fill(cardX + 1, cardY + 1, cardX + 3, cardY + CARD_HEIGHT - 1, 0xFF00E5FF);

        int textX = cardX + PADDING;
        int textY = cardY + 14;

        graphics.drawString(this.font, "§b§lTHORIUM", textX, textY, 0xFF00E5FF, false);
        textY += 14;
        graphics.drawString(this.font, "Join the community", textX, textY, 0xFFFFFFFF, false);
        textY += 18;
        graphics.drawString(this.font, "§7Early builds, tuning help and release notes.", textX, textY, 0xFFB0BEC5, false);
        textY += 12;
        graphics.drawString(this.font, "§b" + ThoriumDiscordInvite.INVITE_LABEL, textX, textY, 0xFF00E5FF, false);
        textY += 16;
        graphics.drawString(this.font, "§8This is shown once. You can always find the link", textX, textY, 0xFF8899A6, false);
        textY += 10;
        graphics.drawString(this.font, "§8in the Thorium settings console.", textX, textY, 0xFF8899A6, false);

        // Widgets (buttons) render on top of the card.
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}