package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.db.BansDatabase.BanLogEntry;
import cbm.server.model.Ban;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Command(name = "log", header = "Show ban log", synopsisHeading = "%nUsage: ",
        description = {"%nShow the ban history either for a player or for a date.%n"})
public class LogCommand implements BotCommand {

    @Option(names = "-p", description = "Force the parameter to be treated as player ID")
    private boolean asPlayer;

    @Parameters(arity = "1", description = {
            "The search parameter. Can be:",
            "- yyyy-mm-dd - specific date",
            "- 'today' or 'yesterday' - convenience relative dates",
            "- steamID (STEAM_0:0:61887661)",
            "- steamID3 ([U:1:123775322])",
            "- steamID64 (76561198084041050)",
            "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
            "- custom URL (robin-the-not-quite-so-brave)",
            "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"
    })
    private String param;

    private static final int SECONDS_PER_DAY = 24 * 3600;

    private final BansDatabase bansDatabase;

    public LogCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        if (asPlayer || parseDate(param) == null)
            return banHistoryForUser(param);

        return banHistoryForDate(param);
    }

    private Flux<String> banHistoryForDate(String dateString) {
        final LocalDate date = parseDate(dateString);
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

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank() || dateString.equalsIgnoreCase("today"))
            return LocalDate.now();

        if (dateString.equalsIgnoreCase("yesterday"))
            return LocalDate.now().minusDays(1);

        try {
            return LocalDate.parse(dateString);
        } catch (Exception e) {
            return null;
        }
    }

    @NotNull
    private Flux<String> banHistoryForUser(String id) {
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
            return composer.compose("No bans found.");

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
        sb.append(entry.getDetectedAt()).append(" [")
          .append(ban.getId()).append("]: \"")
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
