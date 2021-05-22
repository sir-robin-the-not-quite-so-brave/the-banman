package cbm.server.bot;

import cbm.server.BanGenerator;
import cbm.server.SteamID;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import cbm.server.model.OfflineBan;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListBansCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "[*format*] - List the offline bans in the specified format.",
            "- `default`: Normal, *almost* human-readable format.",
            "- `ini`: Suitable for adding to the `PCServer-UDKGame.ini` file."
    };

    private final BansDatabase bansDatabase;

    public ListBansCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "list-bans";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        final BiFunction<List<OfflineBan>, Map<String, Ban>, String> toString;
        switch (params.strip().toLowerCase()) {
            case "ini":
                toString = ListBansCommand::fileBans;
                break;
            case "default":
            case "":
                toString = ListBansCommand::defaultBans;
                break;

            default:
                return Mono.just("Unknown format: " + params);
        }

        final Mono<List<OfflineBan>> offlineBans = bansDatabase.getOfflineBans()
                                                               .collectList();

        final Mono<Map<String, Ban>> currentBans =
                bansDatabase.getCurrentBans()
                            .collectList()
                            .map(bans -> bans.stream()
                                             .collect(Collectors.toMap(Ban::getId, Function.identity())));

        return Mono.zip(offlineBans, currentBans)
                   .map(t -> toString.apply(t.getT1(), t.getT2()));
    }

    private static String fileBans(List<OfflineBan> offlineBans, Map<String, Ban> currentBans) {
        if (offlineBans.isEmpty())
            return "No offline bans";

        final OffsetDateTime now = OffsetDateTime.now();

        return offlineBans.stream()
                          .map(b -> Tuples.of(b, SteamID.steamID(b.getId())))
                          .filter(t -> t.getT2().isPresent())
                          .flatMap(t -> banLines(t.getT1(), t.getT2().get(), now, currentBans))
                          .collect(Collectors.joining("\n"));
    }

    private static Stream<String> banLines(OfflineBan offlineBan, SteamID steamID, OffsetDateTime now,
                                           Map<String, Ban> currentBans) {

        final String banLine = "```\n" + BanGenerator.banLine(steamID,
                                                              offlineBan.getDuration().toSeconds(),
                                                              now,
                                                              offlineBan.getPlayerName(),
                                                              offlineBan.getReason()) + "\n```";

        final Ban ban = currentBans.get(offlineBan.getId());

        if (ban == null)
            return Stream.of(banLine);

        final String comment;
        if (ban.isNetIDBan())
            comment = String.format("Replace previous NetID ban for `BannedIDs=(Uid=(A=%d,B=17825793))`",
                                    steamID.uid());
        else
            comment = String.format("Replace previous ban for `NetId=(Uid=(A=%d,B=17825793))`", steamID.uid());
        return Stream.of("", comment, banLine);
    }

    private static String defaultBans(List<OfflineBan> offlineBans, Map<String, Ban> currentBans) {
        if (offlineBans.isEmpty())
            return "No offline bans";
        return offlineBans.stream()
                          .flatMap(offlineBan -> defaultBan(offlineBan, currentBans))
                          .collect(Collectors.joining("\n", "**Bans:**\n```diff\n", "```"));
    }

    private static Stream<String> defaultBan(OfflineBan offlineBan, Map<String, Ban> currentBans) {
        final Ban ban = currentBans.get(offlineBan.getId());

        if (ban == null)
            return Stream.of("+ " + offlineBan);

        return Stream.of("- " + ban,
                         "+ " + offlineBan);
    }
}
