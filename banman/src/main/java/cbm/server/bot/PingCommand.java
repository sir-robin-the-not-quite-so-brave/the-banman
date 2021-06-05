package cbm.server.bot;

import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import reactor.core.publisher.Flux;

@Command(name = "ping", header = "Ping the bot", synopsisHeading = "%nUsage: ",
        description = {"%nUseful to check if the bot is alive.%n"})
public class PingCommand implements BotCommand {

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        return Flux.just("Pong!");
    }
}
