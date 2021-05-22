package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.SteamID;
import cbm.server.db.BansDatabase;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.List;

public class LogCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* - Shows player's ban history."
    };

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
        for (var ban : banHistory) {
            switch (ban.getAction()) {
                case "add":
                    sb.append("\n+ ");
                    break;

                case "remove":
                    sb.append("\n- ");
                    break;
            }
            sb.append(ban.getDetectedAt()).append(": \"")
              .append(ban.getBan().getPlayerName()).append("\" banned from ")
              .append(ban.getBan().getEnactedTime())
              .append(" until ").append(ban.getBan().getBannedUntil())
              .append(" for \"").append(ban.getBan().getReason()).append("\"");
        }
        sb.append("```");

        return sb.toString();
    }
}
