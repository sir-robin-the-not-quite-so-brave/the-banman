package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "remove-ban", header = "Remove an offline ban")
public class RemoveBanCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>", description = "Steam ID or profile URL")
    private String idOrUrl;

    private final BansDatabase bansDatabase;

    public RemoveBanCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        return Bot.resolveSteamID(idOrUrl)
                  .flatMap(bansDatabase::removeOfflineBan)
                  .map(removed -> removed ? "*Offline ban removed*"
                                          : "*Couldn't find an offline ban for: *" + idOrUrl)
                  .flux();
    }
}
