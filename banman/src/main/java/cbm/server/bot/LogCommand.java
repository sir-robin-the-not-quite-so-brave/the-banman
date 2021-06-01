package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.db.BansDatabase.BanLogEntry;
import cbm.server.model.Ban;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LogCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "[-p] *id-or-url*  - Shows player's ban history.",
            "-d [*yyyy-mm-dd*] - Show bans for given date."
    };
    private static final int SECONDS_PER_DAY = 24 * 3600;

    private final BansDatabase bansDatabase;

    public LogCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "log";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Flux<String> execute(String params, Message message) {
        final String strip = params.strip();
        final String[] split = strip.split("\\s+");
        if (split.length == 0)
            return Flux.just(DESCRIPTION);

        final String secondParam = Optional.of(split)
                                      .filter(s -> s.length > 1)
                                      .map(s -> s[1])
                                      .orElse(null);

        switch (split[0]) {
            case "-d":
                return banHistoryForDate(secondParam);

            case "-p":
                return banHistory(secondParam);

            default:
                return banHistory(split[0]);
        }
    }

    private Flux<String> banHistoryForDate(String dateString) {
        final LocalDate date = parse(dateString);
        if (date == null)
            return Flux.just("Invalid date '" + dateString + ". The format should be `yyyy-mm-dd`.");

        final ZonedDateTime startOfDay = date.atStartOfDay(ZoneOffset.UTC);
        final Instant from = startOfDay.toInstant();
        final Instant to = startOfDay.plusDays(1).toInstant();

        return bansDatabase.getBanHistory(from, to)
                           .collectList()
                           .map(entries -> toString("**Ban history for " + date + "**", entries))
                           .flatMapMany(Flux::fromIterable);
    }

    private LocalDate parse(String dateString) {
        if (dateString == null)
            return LocalDate.now();

        try {
            return LocalDate.parse(dateString);
        } catch (Exception e) {
            return null;
        }
    }

    @NotNull
    private Flux<String> banHistory(String id) {
        return Bot.resolveSteamID(id)
                  .flatMapMany(steamID -> bansDatabase.getBanHistory(steamID)
                                                      .collectList()
                                                      .map(entries -> toString("**Ban history** "
                                                                                       + steamID.profileUrl() + ":",
                                                                               entries))
                                                      .flatMapMany(Flux::fromIterable));
    }

    private static List<String> toString(String header, List<BanLogEntry> banHistory) {
        final MessageComposer composer =
                new MessageComposer.Builder()
                        .setHeader(header)
                        .setPrefix("```diff")
                        .setSuffix("```")
                        .build();

        if (banHistory.isEmpty())
            return composer.compose("*No previous bans.*");

        final List<String> history =
                banHistory.stream()
                          .map(LogCommand::logEntryToString)
                          .collect(Collectors.toList());
        return composer.compose(history);
    }

    private static String logEntryToString(BanLogEntry entry) {
        final StringBuilder sb = new StringBuilder();
        switch (entry.getAction()) {
            case "add":
                sb.append("+ ");
                break;

            case "remove":
                sb.append("- ");
                break;
        }
        final Ban ban = entry.getBan();
        sb.append(entry.getDetectedAt()).append(": \"")
          .append(ban.getPlayerName()).append("\" banned from ")
          .append(ban.getEnactedTime())
          .append(" until ").append(ban.getBannedUntil())
          .append(" (duration ");
        append(sb, Optional.ofNullable(ban.getDuration()).orElse(Duration.ZERO));
        sb.append(") for \"").append(ban.getReason()).append("\"");
        return sb.toString();
    }

    private static void append(StringBuilder sb, Duration duration) {
        final long seconds = duration.toSeconds();
        final long days = seconds / SECONDS_PER_DAY;
        if (days == 0) {
            sb.append(duration);
            return;
        }

        final long weeksPart = days / 7;
        final long daysPart = days % 7;
        final long secondsPart = seconds % SECONDS_PER_DAY;
        final Duration subDayDuration = Duration.ofSeconds(secondsPart);
        sb.append("P");
        if (weeksPart != 0)
            sb.append(weeksPart).append("W");

        if (daysPart != 0)
            sb.append(daysPart).append("D");

        final String s = subDayDuration.toString();
        sb.append(s, 1, s.length());
    }
}
