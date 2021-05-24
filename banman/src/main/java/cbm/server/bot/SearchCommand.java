package cbm.server.bot;

import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class SearchCommand implements BotCommand {
    private static final String[] DESCRIPTION = new String[]{
            "*query* - Search bans."
    };

    private final BansDatabase bansDatabase;

    public SearchCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "search";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        return bansDatabase.queryBans(params, 10)
                           .collectList()
                           .map(this::resultsToString);
    }

    private String resultsToString(List<Ban> results) {
        return results.stream()
                      .map(Ban::toString)
                      .collect(Collectors.joining("\n", "**Results:**\n```\n", "```"));
    }
}
