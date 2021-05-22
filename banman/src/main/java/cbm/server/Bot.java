package cbm.server;

import cbm.server.bot.AddBanCommand;
import cbm.server.bot.BanCommand;
import cbm.server.bot.BotCommand;
import cbm.server.bot.HelpCommand;
import cbm.server.bot.ListBansCommand;
import cbm.server.bot.LogCommand;
import cbm.server.bot.PingCommand;
import cbm.server.bot.ProfileCommand;
import cbm.server.bot.RemoveBanCommand;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "Bot", mixinStandardHelpOptions = true, version = "0.1")
public class Bot implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Logger COMMAND_LOGGER = LogManager.getLogger("command");

    @Option(names = {"-t", "--token"}, required = true)
    private String discordToken;

    @Option(names = {"--db"}, required = true)
    private String bansDatabaseDir;

    @Option(names = {"-c", "--channel"}, arity = "1..*")
    private Set<String> allowedDiscordChannels;

    @Option(names = {"--host"}, required = true)
    private String hostname;

    @Option(names = {"--log-path"}, defaultValue = "/UDKGame/Config/PCServer-UDKGame.ini")
    private String logPath;

    @Option(names = {"-u", "--user"}, required = true)
    private String username;

    @Option(names = {"-p", "--password"}, required = true)
    private String password;

    @Option(names = {"--prefix"}, defaultValue = "!bm")
    private String prefix;

    public static void main(String[] args) {
        final CommandLine commandLine = new CommandLine(new Bot());
        commandLine.execute(args);
    }

    @Override
    public Integer call() {
        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final LogDownloader logDownloader = new LogDownloader(hostname, logPath, username, password);

        final Set<Snowflake> channelIds =
                allowedDiscordChannels.stream()
                                      .map(Snowflake::of)
                                      .collect(Collectors.toSet());
        try (final BansDatabase bansDatabase = new BansDatabase(bansDatabaseDir)) {

            executorService.scheduleAtFixedRate(() -> updateBans(logDownloader, bansDatabase),
                                                10, 3600, TimeUnit.SECONDS);
            executorService.scheduleAtFixedRate(() -> {
                LOGGER.info("Backing up the database ...");
                try {
                    final File backup = bansDatabase.backup();
                    LOGGER.info("Backup created: {}", backup);
                } catch (Exception e) {
                    LOGGER.warn("Failed to backup the database!", e);
                }
            }, 1, 1, TimeUnit.DAYS);


            final Map<String, BotCommand> botCommands =
                    commandsMap(new PingCommand(),
                                new ProfileCommand(),
                                new BanCommand(),
                                new LogCommand(bansDatabase),
                                new AddBanCommand(bansDatabase),
                                new RemoveBanCommand(bansDatabase),
                                new ListBansCommand(bansDatabase));
            final BotCommand helpCommand = botCommands.get("help");

            final GatewayDiscordClient client = DiscordClientBuilder.create(discordToken)
                                                                    .build()
                                                                    .login()
                                                                    .block();

            assert client != null;

            client.getEventDispatcher().on(ReadyEvent.class)
                  .subscribe(event -> {
                      final User self = event.getSelf();
                      LOGGER.info("Logged in as {}#{}. Command prefix is {}",
                                  self.getUsername(), self.getDiscriminator(), prefix);
                  });

            client.getEventDispatcher().on(MessageCreateEvent.class)
                  .map(MessageCreateEvent::getMessage)
                  .filter(message -> channelIds.contains(message.getChannelId()))
                  .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                  .flatMap(this::parse)
                  .flatMap(parsed -> botCommands.getOrDefault(parsed.getT2(), helpCommand)
                                                .execute(parsed.getT3(), parsed.getT1())
                                                .onErrorResume(t -> Mono.justOrEmpty(t.getMessage()))
                                                .flatMap(reply -> replyTo(parsed.getT1(), reply)))
                  .subscribe();
            client.onDisconnect().block();
            return 0;
        }
    }

    private void updateBans(LogDownloader logDownloader, BansDatabase bansDatabase) {
        final Instant lastUpdate = bansDatabase.getLastUpdateSync();
        LOGGER.info("Database is last updated at: {}", lastUpdate);
        final Instant now = Instant.now();
        if (lastUpdate != null && now.isBefore(lastUpdate.plus(1, ChronoUnit.HOURS))) {
            LOGGER.info("Database is up-to-date");
            return;
        }

        try {
            LOGGER.info("Updating database ...");
            final Stream<Ban> banStream = logDownloader.downloadBans();
            final BansDatabase.Stats stats = bansDatabase.storeBans(now, banStream);
            LOGGER.info("Updated complete. {} bans added, {} bans removed", stats.numAdded(), stats.numRemoved());
        } catch (IOException e) {
            LOGGER.warn("Failed to update the database", e);
        }
    }

    @NotNull
    private Mono<Message> replyTo(Message message, String response) {
        return message.getChannel()
                      .flatMap(channel -> channel.createMessage(spec -> spec.setContent(response)
                                                                            .setMessageReference(message.getId())));
    }

    private static Map<String, BotCommand> commandsMap(BotCommand... commands) {
        return Stream.concat(Stream.of(commands),
                             Stream.of(new HelpCommand(commands)))
                     .collect(Collectors.toMap(command -> command.name().toLowerCase(), Function.identity()));
    }

    private Mono<Tuple3<Message, String, String>> parse(Message message) {
        final String[] parts = message.getContent().split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase(prefix))
            return Mono.empty();

        COMMAND_LOGGER.info("{}", message.getContent());

        final String command = Optional.of(parts)
                                       .filter(p -> p.length >= 2)
                                       .map(p -> p[1])
                                       .map(String::toLowerCase)
                                       .orElse("help");

        final StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length; i++)
            sb.append(" ").append(parts[i]);
        final String params = sb.toString().strip();

        return Mono.just(Tuples.of(message, command, params));
    }

    public static Mono<SteamID> resolveSteamID(String id) {
        return SteamID.steamID(id)
                      .map(Mono::just)
                      .orElseGet(() -> SteamWeb.resolveSteamID(id));
    }

    public static Mono<String> getPlayerName(@NotNull SteamID steamID) {
        return SteamWeb.playerProfile(steamID.profileUrl())
                       .map(SteamWeb.Profile::getName)
                       .map(TextUtils::printable);
    }
}
