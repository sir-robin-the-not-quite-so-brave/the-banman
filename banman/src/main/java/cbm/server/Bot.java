package cbm.server;

import cbm.server.bot.AddBanCommand;
import cbm.server.bot.BanCommand;
import cbm.server.bot.ListBansCommand;
import cbm.server.bot.LogCommand;
import cbm.server.bot.PingCommand;
import cbm.server.bot.ProfileCommand;
import cbm.server.bot.RemoveBanCommand;
import cbm.server.bot.SearchCommand;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "Bot", mixinStandardHelpOptions = true, version = "0.1")
public class Bot implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger();

    @Option(names = {"-t", "--token"}, required = true)
    private String discordToken;

    @Option(names = {"--db"}, required = true)
    private String bansDatabaseDir;

    @Option(names = {"-c", "--channel"}, arity = "1..*")
    private Set<String> allowedDiscordChannels;

    @Option(names = {"-r", "--role"}, arity = "0..*")
    private Set<String> allowedRoles;

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
    public Integer call() throws IOException {
        var roles =
                Optional.ofNullable(allowedRoles)
                        .orElse(Collections.emptySet())
                        .stream()
                        .map(s -> s.split(",", 2))
                        .filter(a -> a.length == 2)
                        .collect(Collectors.toMap(a -> Snowflake.of(a[0]),
                                                  a -> Set.of(Snowflake.of(a[1])),
                                                  Bot::union));

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

            final var handler =
                    new MessageHandler(prefix, channelIds, roles,
                                       () -> new CommandLine(new Cmd())
                                                     .addSubcommand(new PingCommand())
                                                     .addSubcommand(new ProfileCommand())
                                                     .addSubcommand(new BanCommand())
                                                     .addSubcommand(new LogCommand(bansDatabase))
                                                     .addSubcommand(new SearchCommand(bansDatabase))
                                                     .addSubcommand(new AddBanCommand(bansDatabase))
                                                     .addSubcommand(new RemoveBanCommand(bansDatabase))
                                                     .addSubcommand(new ListBansCommand(bansDatabase)));

            final GatewayDiscordClient client = DiscordClientBuilder.create(discordToken)
                                                                    .build()
                                                                    .login()
                                                                    .block();

            assert client != null;

            final Duration delay = getDelayUntil(LocalTime.parse("06:00:00"));
            LOGGER.info("Delay to first stats: {}", delay);
            executorService.scheduleAtFixedRate(() -> showStats(bansDatabase, client),
                                                delay.toSeconds(), 24 * 3600, TimeUnit.SECONDS);

            client.getEventDispatcher().on(ReadyEvent.class)
                  .subscribe(event -> {
                      final User self = event.getSelf();
                      LOGGER.info("Logged in as {}#{}. Command prefix is {}",
                                  self.getUsername(), self.getDiscriminator(), prefix);
                  });

            client.getEventDispatcher().on(MessageCreateEvent.class)
                  .map(MessageCreateEvent::getMessage)
                  .flatMap(handler::handle)
                  .subscribe();

            client.onDisconnect().block();
            return 0;
        }
    }

    /**
     * Compute the delay to the specified time (future).
     */
    private Duration getDelayUntil(LocalTime then) {
        final LocalTime now = LocalTime.now();
        final long daysToAdd = now.isAfter(then) ? 1 : 0;
        return Duration.between(now, then).plusDays(daysToAdd);
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

    private void showStats(BansDatabase bansDatabase, GatewayDiscordClient client) {
        final Mono<String> statsMessage =
                getYesterdaysStats(bansDatabase)
                        .map(stats -> String.format("There were %,d bans added and %,d bans removed yesterday.",
                                                    stats.numAdded(), stats.numRemoved()));

        final Mono<String> offlineMessage =
                bansDatabase.getOfflineBans()
                            .collectList()
                            .filter(list -> !list.isEmpty())
                            .map(list -> String.format("\n\nThere are %,d offline bans that need attention. " +
                                                               "Please use `%s list-bans` to see them.",
                                                       list.size(),
                                                       prefix))
                            .defaultIfEmpty("");

        Mono.zip(statsMessage, offlineMessage)
            .map(t -> t.getT1() + t.getT2())
            .flatMapMany(msg -> Flux.fromIterable(allowedDiscordChannels)
                                    .map(Snowflake::of)
                                    .flatMap(id -> client.getChannelById(id)
                                                         .filter(MessageChannel.class::isInstance)
                                                         .cast(MessageChannel.class)
                                                         .flatMap(channel -> channel.createMessage(msg))
                                                         .then()))
            .blockLast();
    }

    private Mono<BansDatabase.Stats> getYesterdaysStats(BansDatabase bansDatabase) {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime to = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        final OffsetDateTime from = to.minusDays(1);
        return bansDatabase.getBanHistory(from.toInstant(), to.toInstant())
                           .collectList()
                           .map(this::stats);
    }

    private BansDatabase.Stats stats(List<BansDatabase.BanLogEntry> logEntries) {
        final Tuple2<Integer, Integer> stats =
                logEntries.stream()
                          .reduce(Tuples.of(0, 0),
                                  (st, entry) -> {
                                      switch (entry.getAction()) {
                                          case "add":
                                              return st.mapT1(n -> n + 1);

                                          case "remove":
                                              return st.mapT2(n -> n + 1);

                                          default:
                                              LOGGER.error("Unknown log entry action: {}", entry.getAction());
                                              return st;
                                      }
                                  },
                                  (st1, st2) -> Tuples.of(st1.getT1() + st2.getT1(),
                                                          st1.getT2() + st2.getT2()));

        return new BansDatabase.Stats() {
            @Override
            public int numAdded() {
                return stats.getT1();
            }

            @Override
            public int numRemoved() {
                return stats.getT2();
            }
        };
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

    public static <T> @NotNull Set<T> union(@NotNull Set<T> s1, @NotNull Set<T> s2) {
        return Stream.concat(s1.stream(), s2.stream())
                     .collect(Collectors.toSet());
    }

    public static <T> @NotNull Set<T> intersection(@NotNull Set<T> s1, @NotNull Set<T> s2) {
        return s1.stream()
                 .filter(s2::contains)
                 .collect(Collectors.toSet());
    }

    @Command(header = "Chivalry Ban Manager.")
    private static class Cmd {
    }
}
