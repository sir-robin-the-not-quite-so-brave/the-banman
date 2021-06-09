package cbm.server;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public final class SteamID {
    private static final long STEAM64 = 0x110000100000000L;
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("STEAM_0:([01]):(\\d+)");
    private static final Pattern PROFILE_URL_PATTERN = Pattern.compile("https?://steamcommunity.com/profiles/(\\d+)/?");

    private final long steamID64;

    public static Optional<SteamID> steamID(@NotNull String id) {
        try {
            return Optional.of(new SteamID(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private SteamID(String id) throws NumberFormatException {
        steamID64 = steamID64(id);
    }

    private static long steamID64(String id) throws NumberFormatException {
        final var mp = PROFILE_URL_PATTERN.matcher(id);
        if (mp.matches())
            return Long.parseLong(mp.group(1));

        final var m = STEAM_ID_PATTERN.matcher(id);
        if (m.matches())
            return Long.parseLong(m.group(2)) * 2 + Integer.parseInt(m.group(1)) + STEAM64;

        final var l = Long.parseLong(id);
        if (l >= STEAM64)
            return l;
        else
            return l + STEAM64;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final var steamID = (SteamID) o;
        return steamID64 == steamID.steamID64;
    }

    @Override
    public int hashCode() {
        return Objects.hash(steamID64);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SteamID.class.getSimpleName() + "[", "]")
                .add("steamID64=" + steamID64)
                .toString();
    }

    public long uid() {
        return steamID64 - STEAM64;
    }

    public long steamID64() {
        return steamID64;
    }

    public String s64() {
        return Long.toString(steamID64);
    }

    public String profileUrl() {
        return "https://steamcommunity.com/profiles/" + steamID64() + "/";
    }

    public String steamID() {
        final long parity = uid() % 2;
        final long id = (uid() - parity) / 2;
        return String.format("STEAM_0:%d:%d", parity, id);
    }

    public String netIDAsString() {
        return String.format("0x%016X", steamID64());
    }
}
