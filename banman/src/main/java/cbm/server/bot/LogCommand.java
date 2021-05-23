package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.SteamID;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

public class LogCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* - Shows player's ban history."
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
    public @NotNull Mono<String> execute(String params) {
        final String id = params.strip();
        return Bot.resolveSteamID(id)
                  .flatMap(steamID -> bansDatabase.getBanHistory(steamID)
                                                  .collectList()
                                                  .flatMap(banHistory -> Mono.just(toString(steamID, banHistory))));
    }

    private static String toString(SteamID steamID, List<BansDatabase.BanLogEntry> banHistory) {
        if (banHistory.isEmpty())
            return "No previous bans.";

        final StringBuilder sb = new StringBuilder("**Ban history** ");
        sb.append(steamID.profileUrl()).append(":\n```diff");
        for (var banLogEntry : banHistory) {
            switch (banLogEntry.getAction()) {
                case "add":
                    sb.append("\n+ ");
                    break;

                case "remove":
                    sb.append("\n- ");
                    break;
            }
            final Ban ban = banLogEntry.getBan();
            sb.append(banLogEntry.getDetectedAt()).append(": \"")
              .append(ban.getPlayerName()).append("\" banned from ")
              .append(ban.getEnactedTime())
              .append(" until ").append(ban.getBannedUntil())
              .append(" (duration ");
            append(sb, ban.getDuration());
            sb.append(") for \"").append(ban.getReason()).append("\"");
        }
        sb.append("```");

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
