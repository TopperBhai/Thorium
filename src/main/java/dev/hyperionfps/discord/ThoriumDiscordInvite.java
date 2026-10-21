package dev.hyperionfps.discord;

import dev.hyperionfps.config.ThoriumConfig;

/**
 * Policy for the one-time community invite.
 *
 * <p>Thorium shows a single invite card the first time the player sees the main menu.
 * The card is deliberately <b>one</b> card: it is offered once, it can be dismissed
 * with a single click, and it never appears again. A performance mod that nags is a
 * performance mod that gets uninstalled, so the invite is opt-out rather than opt-in
 * and the player always keeps the link available in the settings console.</p>
 *
 * <p>The browser hand-off itself is era-specific - 1.21.x opens
 * {@code net.minecraft.Util.getPlatform().openUri(String)} while 26.x uses
 * {@code com.mojang.blaze3d.Blaze3D.openUri(URI)} - so it lives in the per-version
 * {@code ThoriumDiscordScreen}. Only the policy (which link, when to show it, and when
 * it has been seen) is shared.</p>
 */
public final class ThoriumDiscordInvite {

    /** The community invite. */
    public static final String INVITE_URL = "https://discord.gg/TbJtmvJKB3";

    /** The invite without the scheme - what the card displays. */
    public static final String INVITE_LABEL = "discord.gg/TbJtmvJKB3";

    private ThoriumDiscordInvite() {
    }

    /**
     * Whether the first-launch invite may still be shown.
     *
     * <p>Read straight from the persisted configuration instead of being pushed into a
     * subsystem: the invite is a one-shot event, not a running optimisation, so there
     * is no live state to restore during client initialisation.</p>
     */
    public static boolean isEnabled() {
        return ThoriumConfig.get().getBoolean(ThoriumConfig.KEY_DISCORD_INVITE_ENABLED, true);
    }

    /** Turns the first-launch invite on or off. */
    public static void setEnabled(boolean enabled) {
        ThoriumConfig config = ThoriumConfig.get();
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_ENABLED, enabled);
        config.save();
    }

    /** Whether the invite has already been offered (accepted <em>or</em> dismissed). */
    public static boolean isShown() {
        return ThoriumConfig.get().getBoolean(ThoriumConfig.KEY_DISCORD_INVITE_SHOWN, false);
    }

    /** Records that the invite has been offered. It is never offered again. */
    public static void markShown() {
        ThoriumConfig config = ThoriumConfig.get();
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_SHOWN, true);
        config.save();
    }

    /** Whether the invite should be shown right now. */
    public static boolean shouldShow() {
        return isEnabled() && !isShown();
    }
}
