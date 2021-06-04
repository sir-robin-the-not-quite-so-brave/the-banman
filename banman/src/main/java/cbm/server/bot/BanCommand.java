package cbm.server.bot;

import cbm.server.BanGenerator;
import cbm.server.Bot;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

@Command(name = "ban", header = "Generate a ban line")
public class BanCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>", description = "Steam ID or profile URL")
    private String idOrUrl;

    @Parameters(index = "1", paramLabel = "<period>", description = "Ban period. `P0D` means permanent ban.")
    private Period period;

    @Parameters(index = "2..*", arity = "1..*", description = "Ban reason")
    private String[] reason;

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        final OffsetDateTime now = OffsetDateTime.now();
        final long seconds = ChronoUnit.SECONDS.between(now, now.plus(period));

        final String r = String.join(" ", reason);

        return Bot.resolveSteamID(idOrUrl)
                  .flatMap(id -> Bot.getPlayerName(id)
                                    .switchIfEmpty(Mono.just("player_" + id.steamID64()))
                                    .flatMap(name -> Mono.just("`" + BanGenerator.banLine(id,
                                                                                          seconds,
                                                                                          null,
                                                                                          name,
                                                                                          r) + "`")))
                  .flux();
    }
}
