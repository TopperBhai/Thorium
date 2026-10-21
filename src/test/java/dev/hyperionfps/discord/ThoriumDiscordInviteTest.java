package dev.hyperionfps.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.config.ThoriumConfig;

class ThoriumDiscordInviteTest {

    @BeforeEach
    void setUp() {
        ThoriumConfig config = ThoriumConfig.get();
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_ENABLED, true);
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_SHOWN, false);
    }

    @AfterEach
    void tearDown() {
        ThoriumConfig config = ThoriumConfig.get();
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_ENABLED, true);
        config.setBoolean(ThoriumConfig.KEY_DISCORD_INVITE_SHOWN, false);
    }

    @Test
    void inviteIsOfferedUntilItHasBeenSeen() {
        assertTrue(ThoriumDiscordInvite.shouldShow());

        ThoriumDiscordInvite.markShown();

        assertTrue(ThoriumDiscordInvite.isShown());
        assertFalse(ThoriumDiscordInvite.shouldShow(), "the invite must never be offered twice");
    }

    @Test
    void inviteCanBeTurnedOff() {
        ThoriumDiscordInvite.setEnabled(false);
        assertFalse(ThoriumDiscordInvite.isEnabled());
        assertFalse(ThoriumDiscordInvite.shouldShow());

        ThoriumDiscordInvite.setEnabled(true);
        assertTrue(ThoriumDiscordInvite.shouldShow());
    }

    @Test
    void invitePointsAtTheCommunityServer() {
        assertEquals("https://discord.gg/TbJtmvJKB3", ThoriumDiscordInvite.INVITE_URL);
        assertTrue(ThoriumDiscordInvite.INVITE_LABEL.contains("TbJtmvJKB3"));
        assertFalse(ThoriumDiscordInvite.INVITE_LABEL.startsWith("http"),
                "the card shows the bare invite, not the full URL");
    }
}
