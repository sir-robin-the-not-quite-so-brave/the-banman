package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.model.OfflineBan;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

@Command(name = "add-ban", header = "Add an offline ban")
public class AddBanCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>", description = "Steam ID or profile URL")
    private String idOrUrl;

    @Parameters(index = "1", paramLabel = "<period>", description = "Ban period. `P0D` means permanent ban.")
    private Period period;

    @Parameters(index = "2..*", arity = "1..*", description = "Ban reason")
    private String[] reason;

    private final BansDatabase bansDatabase;

    public AddBanCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        final OffsetDateTime now = OffsetDateTime.now();
        final long seconds = ChronoUnit.SECONDS.between(now, now.plus(period));
        final String reason = String.join(" ", this.reason)
                                      + message.getAuthor()
                                               .map(User::getUsername)
                                               .map(s -> " - " + s)
                                               .orElse("");

        return Bot.resolveSteamID(idOrUrl)
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
    }
}
