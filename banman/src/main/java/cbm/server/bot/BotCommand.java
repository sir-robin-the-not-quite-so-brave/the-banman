package cbm.server.bot;

import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public interface BotCommand {
    @NotNull Flux<String> execute(@NotNull Message message);
}
