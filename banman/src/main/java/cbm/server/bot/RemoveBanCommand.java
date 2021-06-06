package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "remove-ban", header = "Remove an offline ban", synopsisHeading = "%nUsage: ",
        description = {"%nRemove an offline ban from the list.%n"})
public class RemoveBanCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>",
            description = {"Steam ID or profile URL. The supported formats are:",
                    "- steamID (STEAM_0:0:61887661)",
                    "- steamID3 ([U:1:123775322])",
                    "- steamID64 (76561198084041050)",
                    "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
                    "- custom URL (robin-the-not-quite-so-brave)",
                    "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"})
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
