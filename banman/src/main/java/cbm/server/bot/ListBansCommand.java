package cbm.server.bot;

import cbm.server.BanGenerator;
import cbm.server.SteamID;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import cbm.server.model.OfflineBan;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public @NotNull Flux<String> execute(String params, Message message) {
        final BiFunction<List<OfflineBan>, Map<String, Ban>, List<String>> toString;
        switch (params.strip().toLowerCase()) {
            case "ini":
                toString = ListBansCommand::fileBans;
                break;
            case "default":
            case "":
                toString = ListBansCommand::defaultBans;
                break;

            default:
                return Flux.just("Unknown format: " + params);
        }

        final Mono<List<OfflineBan>> offlineBans = bansDatabase.getOfflineBans()
                                                               .collectList();

        final Mono<Map<String, Ban>> currentBans =
                bansDatabase.getCurrentBans()
                            .collectList()
                            .map(bans -> bans.stream()
                                             .collect(Collectors.toMap(Ban::getId, Function.identity())));

        return Mono.zip(offlineBans, currentBans)
                   .flatMapIterable(t -> toString.apply(t.getT1(), t.getT2()));
    }

    private static List<String> fileBans(List<OfflineBan> offlineBans, Map<String, Ban> currentBans) {
        if (offlineBans.isEmpty())
            return Collections.singletonList("No offline bans");

        final OffsetDateTime now = OffsetDateTime.now();

        final MessageComposer composer = new MessageComposer.Builder().build();

        final List<String> banInfos =
                offlineBans.stream()
                           .map(b -> Tuples.of(b, SteamID.steamID(b.getId())))
                           .filter(t -> t.getT2().isPresent())
                           .map(t -> banLines(t.getT1(), t.getT2().get(), now, currentBans))
                           .collect(Collectors.toList());

        return composer.compose(banInfos);
    }

    private static String banLines(OfflineBan offlineBan, SteamID steamID, OffsetDateTime now,
                                   Map<String, Ban> currentBans) {

        final OffsetDateTime enactedTime = Optional.ofNullable(offlineBan.getEnactedTime())
                                                   .map(instant -> OffsetDateTime.ofInstant(instant, ZoneId.of("UTC")))
                                                   .orElse(now);
        final String banLine = "```\n" + BanGenerator.banLine(steamID,
                                                              offlineBan.getDuration().toSeconds(),
                                                              enactedTime,
                                                              offlineBan.getPlayerName(),
                                                              offlineBan.getReason()) + "\n```";

        final Ban ban = currentBans.get(offlineBan.getId());

        if (ban == null)
            return banLine;

        final String comment;
        if (ban.isNetIDBan())
            comment = String.format("Replace previous NetID ban for `BannedIDs=(Uid=(A=%d,B=17825793))`",
                                    steamID.uid());
        else
            comment = String.format("Replace previous ban for `NetId=(Uid=(A=%d,B=17825793))`", steamID.uid());
        return String.join("\n", "", comment, banLine);
    }

    private static List<String> defaultBans(List<OfflineBan> offlineBans, Map<String, Ban> currentBans) {
        if (offlineBans.isEmpty())
            return Collections.singletonList("*No offline bans*");

        final MessageComposer composer = new MessageComposer.Builder()
                                                 .setHeader("**Bans:**")
                                                 .setPrefix("```diff")
                                                 .setSuffix("```")
                                                 .build();

        return composer.compose(offlineBans.stream()
                                           .map(offlineBan -> defaultBan(offlineBan, currentBans))
                                           .collect(Collectors.toList()));

    }

    private static String defaultBan(OfflineBan offlineBan, Map<String, Ban> currentBans) {
        final Ban ban = currentBans.get(offlineBan.getId());

        if (ban == null)
            return "+ " + offlineBan;

        return String.join("\n", "- " + ban,
                           "+ " + offlineBan);
    }
}
