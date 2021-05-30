package cbm.server.bot;

import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BotCommand {

    @NotNull String name();

    @NotNull String[] description();

    default @NotNull Mono<String> execute(String params) {
        return Mono.just(params);
    }

    default @NotNull Flux<String> execute(String params, Message message) {
        return execute(params).flux();
    }
}
