package cbm.server.bot;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class PingCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "Ping."
    };

    @Override
    public @NotNull String name() {
        return "ping";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        return Mono.just("Pong!");
    }
}
