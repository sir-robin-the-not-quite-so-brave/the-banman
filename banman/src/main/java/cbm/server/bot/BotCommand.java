package cbm.server.bot;

import cbm.server.MessageHandler;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public interface BotCommand {
    default @NotNull Flux<String> execute(@NotNull Message message) {
        return Flux.just("**Not implemented**");
    }

    default @NotNull Flux<Message> executeFull(@NotNull Message message) {
        return execute(message)
                       .flatMap(s -> MessageHandler.replyTo(message, s));
    }
}
