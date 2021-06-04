package cbm.server.bot;

import cbm.server.Bot;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

@Command(name = "profile", header = "Show the *canonical* Steam profile URL")
public class ProfileCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>", description = "Steam ID or profile URL")
    private String idOrUrl;

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        return Bot.resolveSteamID(idOrUrl)
                  .map(steamID -> "**Profile URL:** " + steamID.profileUrl())
                  .flux();
    }
}
