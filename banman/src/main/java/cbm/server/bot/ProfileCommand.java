package cbm.server.bot;

import cbm.server.Bot;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class ProfileCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* - Gives the *canonical* URL to the Steam profile."
    };

    @Override
    public @NotNull String name() {
        return "profile";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        final String id = params.strip();
        return Bot.resolveSteamID(id)
                  .map(steamID -> "**Profile URL:** " + steamID.profileUrl());
    }
}
