package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class RemoveBanCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* - Remove the offline ban."
    };

    private final BansDatabase bansDatabase;

    public RemoveBanCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "remove-ban";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        final String id = params.strip();
        return Bot.resolveSteamID(id)
                  .flatMap(bansDatabase::removeOfflineBan)
                  .map(removed -> removed ? "Offline ban removed"
                                          : "Couldn't find an offline ban for: " + id);
    }
}
