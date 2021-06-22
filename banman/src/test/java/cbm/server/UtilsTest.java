package cbm.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    public void testUrlExtraction() {
        var urls =
                Utils.extractUrls("here is his profile https://steamcommunity.com/profiles/76561199073125902. " +
                                          "I'll add a ban probably tomorrow");

        assertEquals(1, urls.size());
        assertEquals("https://steamcommunity.com/profiles/76561199073125902", urls.get(0));
    }
}
