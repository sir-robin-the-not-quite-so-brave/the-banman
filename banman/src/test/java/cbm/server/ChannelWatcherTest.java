package cbm.server;

import org.junit.jupiter.api.Test;

import static cbm.server.ChannelWatcher.isProfileUrl;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelWatcherTest {

    @SuppressWarnings("HttpUrlsUsage")
    @Test
    public void testSteamProfile() {
        assertTrue(isProfileUrl("https://steamcommunity.com/profiles/123"));
        assertTrue(isProfileUrl("http://steamcommunity.com/profiles/123"));
        assertTrue(isProfileUrl("https://steamcommunity.com/id/123"));
        assertTrue(isProfileUrl("http://steamcommunity.com/id/123"));
        assertTrue(isProfileUrl("https://steamCommunity.com/profiles/123"));
        assertTrue(isProfileUrl("http://Steamcommunity.com/profiles/123"));
        assertTrue(isProfileUrl("https://SteamCommunity.com/id/123"));
        assertTrue(isProfileUrl("http://steamcommunity.com/id/123"));
        assertFalse(isProfileUrl("http://steamcommunity.com/ids/123"));
        assertFalse(isProfileUrl("http://streamcommunity.com/id/123"));
        assertFalse(isProfileUrl("http://steamcommunity.com/profilei/123"));
        assertFalse(isProfileUrl("http://steamcommunity.com/profilesd/123"));
        assertFalse(isProfileUrl("http://steamcommunity.com/profileid/123"));
        assertFalse(isProfileUrl("http://steamcommunity.com/profiled/123"));
    }
}
