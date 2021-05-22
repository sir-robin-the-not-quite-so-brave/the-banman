package cbm.server.bot;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class HelpCommand implements BotCommand {

    private static final String[] DESCRIPTION = new String[]{
            "[*command*] - Without parameter shows all commands.",
            "              With parameter shows the help message for this command."
    };

    private static final String[] FOOTER = new String[]{
            "----",
            "**Parameters**:",
            "- *id-or-url* - This could be steamID (`STEAM_0:0:61887661`), steamID3 (`[U:1:123775322]`), " +
                    "steamID64 (`76561198084041050`), full profile URL or custom URL (`robin-the-not-quite-so-brave` " +
                    "or `https://steamcommunity.com/id/robin-the-not-quite-so-brave`).",
            "- *period*    - Ban period. Can be `P0D` - for permanent ban, `P7D` (or `P1W`) for 7 day, " +
                    "`P2M` for 2 months, `P1Y` for 1 year. Or even `P1Y2M3W4D`."
    };

    private final Map<String, String> usage;

    public HelpCommand(BotCommand... commands) {
        this.usage =
                Stream.concat(Stream.of(commands), Stream.of(this))
                      .collect(toMap(command -> command.name().toLowerCase(),
                                     this::helpMessage,
                                     (s, s2) -> {
                                         if (Objects.equals(s, s2))
                                             return s;

                                         final String msg = String.format("Duplicate key for values %s and %s", s, s2);
                                         throw new IllegalStateException(msg);
                                     },
                                     LinkedHashMap::new));
    }

    private @NotNull String helpMessage(BotCommand command) {
        final StringBuilder sb = new StringBuilder();
        sb.append("**").append(command.name()).append("**:");
        for (var d : command.description())
            sb.append("\n    ").append(d);
        return sb.toString();
    }

    @Override
    public @NotNull String name() {
        return "help";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params) {
        final String cmd = params.trim().toLowerCase();
        final String u = usage.get(cmd);
        final Stream<String> commands;
        if (u != null)
            commands = Stream.of(u);
        else
            commands = usage.values().stream();

        return Mono.just(Stream.concat(commands, Stream.of(FOOTER))
                               .collect(Collectors.joining("\n", "**Usage**:", "")));
    }
}
