package cbm.server.bot;

import cbm.server.BanGenerator;
import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.model.OfflineBan;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

@Command(name = "add-ban", header = "Add an offline ban", synopsisHeading = "%nUsage: ",
        description = {"%nAdd an offline ban for the specified player. For the ban to take" +
                               " effect, an admin has to update the PCServer-UDKGame.ini file.",
                "See the 'list-bans' command for more details.%n"})
public class AddBanCommand implements BotCommand {

    @Option(names = "-f", description = "Force - if there is an existing offline ban, replace it")
    private boolean force;

    @Option(names = "-g", description = "Only generate the ban line without adding it to the offline bans")
    private boolean onlyGenerate;

    @Parameters(index = "0", paramLabel = "<id-or-url>",
            description = {"Steam ID or profile URL. The supported formats are:",
                    "- steamID (STEAM_0:0:61887661)",
                    "- steamID3 ([U:1:123775322])",
                    "- steamID64 (76561198084041050)",
                    "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
                    "- custom URL (robin-the-not-quite-so-brave)",
                    "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"})
    private String idOrUrl;

    @Parameters(index = "1", paramLabel = "<period>",
            description = {"Ban period. Examples:",
                    "- P0D - permanent ban",
                    "- P7D - 7 days ban",
                    "- P1W - 7 days ban",
                    "- P2M - 2 months ban",
                    "- P1Y - 1 year ban",
                    "- P1Y2M3W4D - 1 year, 2 months, 3 weeks and 4 days ban"})
    private Period period;

    @Parameters(index = "2..*", arity = "1..*",
            description = "Ban reason. The name of the banning admin will be added automatically" +
                                  " to the provided reason")
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

        if (onlyGenerate)
            return Bot.resolveSteamID(idOrUrl)
                      .flatMapMany(id -> Bot.getPlayerName(id)
                                            .switchIfEmpty(Mono.just("player_" + id.steamID64()))
                                            .flatMap(name -> Mono.just("`" + BanGenerator.banLine(id,
                                                                                                  seconds,
                                                                                                  null,
                                                                                                  name,
                                                                                                  reason) + "`")));

        return Bot.resolveSteamID(idOrUrl)
                  .flatMapMany(id -> Bot.getPlayerName(id)
                                        .switchIfEmpty(Mono.just("player_" + id.steamID64()))
                                        .map(name -> new OfflineBan.Builder()
                                                             .setId(id.s64())
                                                             .setDurationSeconds(seconds)
                                                             .setPlayerName(name)
                                                             .setReason(reason)
                                                             .build())
                                        .flatMap(offlineBan -> bansDatabase.addOfflineBan(offlineBan, force))
                                        .map(added -> added ? "Ban successfully saved."
                                                            : "There is an existing offline ban for this player. " +
                                                                      "Use the -f option to overwrite it."));
    }
}
