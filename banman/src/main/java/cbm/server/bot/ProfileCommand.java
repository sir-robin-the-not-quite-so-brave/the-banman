package cbm.server.bot;

import cbm.server.Bot;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "profile", header = "Show the canonical Steam profile URL", synopsisHeading = "%nUsage: ",
        description = {"%nUnlike the custom URL, the canonical one cannot be changed by the player.%n"})
public class ProfileCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>",
            description = {"Steam ID or profile URL. The supported formats are:",
                    "- steamID (STEAM_0:0:61887661)",
                    "- steamID3 ([U:1:123775322])",
                    "- steamID64 (76561198084041050)",
                    "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
                    "- custom URL (robin-the-not-quite-so-brave)",
                    "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"})
    private String idOrUrl;

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        return Bot.resolveSteamID(idOrUrl)
                  .map(steamID -> "**Profile URL:** " + steamID.profileUrl())
                  .flux();
    }
}
