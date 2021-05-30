package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.model.OfflineBan;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

public class AddBanCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "*id-or-url* *period* *reason* - Add an offline. The *reason* will automatically contain the name of the " +
                    "admin, who's adding the ban."
    };

    private final BansDatabase bansDatabase;

    public AddBanCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "add-ban";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Flux<String> execute(String params, Message message) {
        try {
            final String[] split = params.split("\\s+", 3);
            if (split.length != 3)
                throw new IllegalArgumentException("Bad parameters");

            final Period period = Period.parse(split[1]);
            final OffsetDateTime now = OffsetDateTime.now();
            final long seconds = ChronoUnit.SECONDS.between(now, now.plus(period));
            final String reason = split[2].strip()
                    + message.getAuthor()
                             .map(User::getUsername)
                             .map(s -> " - " + s)
                             .orElse("");

            return Bot.resolveSteamID(split[0])
                      .flatMapMany(id -> Bot.getPlayerName(id)
                                            .switchIfEmpty(Mono.just("player_" + id.steamID64()))
                                            .map(name -> new OfflineBan.Builder()
                                                                 .setId(Long.toString(id.steamID64()))
                                                                 .setDurationSeconds(seconds)
                                                                 .setPlayerName(name)
                                                                 .setReason(reason)
                                                                 .build())
                                            .flatMap(bansDatabase::addOfflineBan)
                                            .map(added -> added ? "Ban successfully saved"
                                                                : "There is an existing offline ban for this player"));
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
    }
}
