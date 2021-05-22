package cbm.server.bot;

import cbm.server.BanGenerator;
import cbm.server.Bot;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

public class BanCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* *period* *reason* - Generate a ban line. This can be included in the `PCServer-UDKGame.ini` " +
                    "file to add an offline ban."
    };

    @Override
    public @NotNull String name() {
        return "ban";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        try {
            final String[] split = params.split("\\s+", 3);
            if (split.length != 3)
                throw new IllegalArgumentException("Bad parameters");

            final Period period = Period.parse(split[1]);
            final OffsetDateTime now = OffsetDateTime.now();
            final long seconds = ChronoUnit.SECONDS.between(now, now.plus(period));
            final String reason = split[2].strip();

            return Bot.resolveSteamID(split[0])
                      .flatMap(id -> Bot.getPlayerName(id)
                                        .switchIfEmpty(Mono.just("player_" + id.steamID64()))
                                        .flatMap(name -> Mono.just("`" + BanGenerator.banLine(id,
                                                                                              seconds,
                                                                                              null,
                                                                                              name,
                                                                                              reason) + "`")));

        } catch (RuntimeException e) {
            return Mono.error(e);
        }
    }
}
