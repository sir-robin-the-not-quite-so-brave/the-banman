package cbm.server;

import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Optional;

public class BanGenerator {
    // (Year=2019,Month=5,DayOfWeek=3,Day=1,Hour=18,Min=23,Sec=51,MSec=917)
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendLiteral("(Year=")
            .appendValue(ChronoField.YEAR_OF_ERA)
            .appendLiteral(",Month=")
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral(",DayOfWeek=")
            .appendValue(ChronoField.DAY_OF_WEEK)
            .appendLiteral(",Day=")
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(",Hour=")
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(",Min=")
            .appendValue(ChronoField.MINUTE_OF_HOUR)
            .appendLiteral(",Sec=")
            .appendValue(ChronoField.SECOND_OF_MINUTE)
            .appendLiteral(",MSec=")
            .appendValue(ChronoField.MILLI_OF_SECOND)
            .appendLiteral(")")
            .toFormatter();

    public static String banLine(@NotNull SteamID steamID, long seconds, OffsetDateTime enacted, String playerName,
                                 String reason) {
        final OffsetDateTime offsetDateTime = Optional.ofNullable(enacted)
                                                      .orElseGet(() -> OffsetDateTime.now(ZoneOffset.UTC));
        return String.format(
                "Bans=(DurationSeconds=%d,EnactedTime=%s,IPPolicy=\"DENY,0.0.0.0\",NetId=(Uid=(A=%d,B=17825793)),PlayerName=\"%s\",Reason=\"%s\",NetIDAsString=\"%s\")",
                seconds,
                DATE_TIME_FORMATTER.format(offsetDateTime),
                steamID.uid(),
                playerName,
                reason,
                steamID.netIDAsString());
    }
}
