package cbm.server.imp;

import cbm.server.SteamID;
import cbm.server.model.Ban;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseBan {
    private static final Logger logger = LogManager.getLogger();

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class,
                                 new FromStringDeserializer<>(OffsetDateTime::parse, ParseBan::fixInstantFormat))
            .registerTypeAdapter(Instant.class,
                                 new FromStringDeserializer<>(Instant::parse, ParseBan::fixInstantFormat))
            .registerTypeAdapter(Period.class, new FromStringDeserializer<>(Period::parse))
            .registerTypeAdapter(Duration.class, new FromStringDeserializer<>(Duration::parse))
            .create();

    private static final Pattern BAN_PATTERN =
            Pattern.compile("https?://steamcommunity.com/profiles/\\d+ - " +
                                    "Ban\\{steamId=.+, steamId64=(\\d+), duration=(\\S+), enactedTime=(\\S+), " +
                                    "bannedUntil=\\S+, ipPolicy=(\\S+), playerName=(.+), reason=(.+), netId=.+}");

    public static List<Ban> parseBans(String s) {
        final List<Ban> bans = new ArrayList<>();
        final var lines = s.split("\n");
        for (var line : lines)
            parseBan(line).ifPresentOrElse(bans::add,
                                           () -> logger.warn("Can't parse line: {}", line));

        return bans;
    }

    public static Optional<Ban> parseBan(String line) {
        if (line.startsWith("{"))
            return parseJsonBan(line);
        else
            return parseOldBan(line);
    }

    private static Optional<Ban> parseJsonBan(String line) {
        final LoggedBan loggedBan = gson.fromJson(line, LoggedBan.class);
        return Optional.of(loggedBan.builder().build());
    }

    private static Optional<Ban> parseOldBan(String line) {
        final Matcher m = BAN_PATTERN.matcher(line);
        if (!m.matches())
            return Optional.empty();

        final String steamId64 = m.group(1);
        final String duration = m.group(2);
        final String enactedTime = m.group(3);
        final String ipPolicy = m.group(4);
        final String playerName = m.group(5);
        final String reason = m.group(6);

        final Ban.Builder builder = new Ban.Builder()
                .setId(steamId64);

        if (!duration.equals("null"))
            builder.setDuration(Duration.parse(duration));
        if (!enactedTime.equals("null"))
            builder.setEnactedTime(Instant.parse(fixInstantFormat(enactedTime)));
        if (!ipPolicy.equals("null"))
            builder.setIpPolicy(ipPolicy);
        if (!playerName.equals("null"))
            builder.setPlayerName(playerName);
        if (!reason.equals("null"))
            builder.setReason(reason);

        return Optional.of(builder.build());
    }

    private static String fixInstantFormat(String s) {
        if (s.length() == "2013-09-10T11:35".length())
            return s + ":00Z";

        if (s.length() == "2013-09-10T11:35Z".length())
            return s.substring(0, s.length() - 1) + ":00Z";

        if (s.length() == "2013-09-10T11:35:00".length())
            return s + "Z";

        return s;
    }

    public static class FromStringDeserializer<T> implements JsonDeserializer<T> {
        private final Function<String, T> deserializerFn;

        public FromStringDeserializer(Function<String, T> deserializerFn) {
            this(deserializerFn, Function.identity());
        }

        public FromStringDeserializer(Function<String, T> deserializerFn, Function<String, String> fixer) {
            this.deserializerFn = fixer.andThen(deserializerFn);
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {

            final String s = json.getAsString();
            return deserializerFn.apply(s);
        }
    }

    public static class LoggedBan {
        @SerializedName("profile-url")
        public String profileUrl;
        @SerializedName("enacted-time")
        public Instant enactedTime;
        public Duration duration;
        @SerializedName("ip-policy")
        public String ipPolicy;
        @SerializedName("player-name")
        public String playerName;
        public String reason;

        public Ban.Builder builder() {
            final String id = SteamID.steamID(profileUrl).map(SteamID::steamID64)
                                     .map(Objects::toString)
                                     .orElseThrow();
            return new Ban.Builder()
                    .setId(id)
                    .setEnactedTime(enactedTime)
                    .setDuration(duration)
                    .setIpPolicy(ipPolicy)
                    .setPlayerName(playerName)
                    .setReason(reason);
        }
    }
}
