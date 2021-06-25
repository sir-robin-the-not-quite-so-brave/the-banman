package cbm.server;

import cbm.server.bot.AddBanCommand;
import cbm.server.bot.ColorCommand;
import cbm.server.bot.ListBansCommand;
import cbm.server.bot.LogCommand;
import cbm.server.bot.PingCommand;
import cbm.server.bot.ProfileCommand;
import cbm.server.bot.RemoveBanCommand;
import cbm.server.bot.SearchCommand;
import cbm.server.bot.WantedCommand;
import cbm.server.db.BansDatabase;
import cbm.server.model.Ban;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Command(name = "Bot", mixinStandardHelpOptions = true, version = "1.0")
public class Bot implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger();

    @Option(names = {"-t", "--token"}, required = true, description = "The Discord bot token.")
    private String discordToken;

    @Option(names = {"--reset-watchlist"}, description = "Resets the watch database.")
    private boolean resetWatchlist;

    @ArgGroup(exclusive = false, heading = "Chivalry server options:%n")
    private ChivalryServer chivalryServer;

    @Parameters(arity = "1", paramLabel = "<path/to/conf.toml>", description = "The Bot configuration.")
    private Path configurationPath;

    public static void main(String[] args) {
        final CommandLine commandLine = new CommandLine(new Bot());
        commandLine.execute(args);
    }

    @Override
    public Integer call() throws IOException {
        final Configuration configuration = Configuration.load(configurationPath);

        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final LogDownloader logDownloader = new LogDownloader(chivalryServer);

        try (final BansDatabase bansDatabase = new BansDatabase(configuration.getDatabase())) {
            if (resetWatchlist)
                bansDatabase.clearMentionsData();

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
                    new MessageHandler(configuration,
                                       () -> new CommandLine(new Cmd())
                                                     .addSubcommand(new PingCommand())
                                                     .addSubcommand(new ColorCommand())
                                                     .addSubcommand(new ProfileCommand())
                                                     .addSubcommand(new LogCommand(bansDatabase))
                                                     .addSubcommand(new SearchCommand(bansDatabase))
                                                     .addSubcommand(new WantedCommand(bansDatabase))
                                                     .addSubcommand(new AddBanCommand(bansDatabase))
                                                     .addSubcommand(new RemoveBanCommand(bansDatabase))
                                                     .addSubcommand(new ListBansCommand(bansDatabase)));

            final GatewayDiscordClient client =
                    Objects.requireNonNull(DiscordClientBuilder.create(discordToken)
                                                               .build()
                                                               .login()
                                                               .block());

            final Duration delay = getDelayUntil(LocalTime.parse("06:00:00"));
            LOGGER.info("Delay to first stats: {}", delay);
            executorService.scheduleAtFixedRate(() -> showStats(bansDatabase, client, configuration),
                                                delay.toSeconds(), 24 * 3600, TimeUnit.SECONDS);

            client.getEventDispatcher().on(ReadyEvent.class)
                  .subscribe(event -> {
                      final User self = event.getSelf();
                      LOGGER.info("Logged in as {}#{}. Command prefix is {}",
                                  self.getUsername(), self.getDiscriminator(), configuration.getPrefix());
                  });

            client.getEventDispatcher().on(MessageCreateEvent.class)
                  .map(MessageCreateEvent::getMessage)
                  .flatMap(handler::handle)
                  .subscribe();

            final Set<Snowflake> watchListChannels = configuration.getWatchListChannels();

            client.getEventDispatcher().on(MessageCreateEvent.class)
                  .map(MessageCreateEvent::getMessage)
                  .filter(message -> watchListChannels.contains(message.getChannelId()))
                  .flatMap(message -> message.getChannel()
                                             .cast(GuildMessageChannel.class)
                                             .flatMap(channel -> new ChannelWatcher(channel, bansDatabase)
                                                                         .updateChannel(message.getId()))
                                             .doOnNext(stats -> LOGGER.info("Processed mentions: {}", stats)))
                  .subscribe();

            updateMentions(bansDatabase, client, watchListChannels)
                    .subscribe();

            client.onDisconnect().block();
            return 0;
        }
    }

    private Mono<Void> updateMentions(BansDatabase bansDatabase, GatewayDiscordClient client,
                                      Set<Snowflake> channelIds) {

        return Flux.fromIterable(channelIds)
                   .flatMap(client::getChannelById)
                   .filter(GuildMessageChannel.class::isInstance)
                   .cast(GuildMessageChannel.class)
                   .doOnNext(channel -> LOGGER.info("Monitoring channel #{}", channel.getName()))
                   .flatMap(channel -> new ChannelWatcher(channel, bansDatabase).updateChannel())
                   .doOnNext(stats -> LOGGER.info("Processed mentions: {}", stats))
                   .then();
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

    private void showStats(BansDatabase bansDatabase, GatewayDiscordClient client, Configuration conf) {
        final Mono<BansDatabase.Stats> yesterdaysStats = getYesterdaysStats(bansDatabase);
        final Mono<Integer> offlineBansCount = bansDatabase.getOfflineBans()
                                                           .collectList()
                                                           .map(List::size);
        Mono.zip(yesterdaysStats, offlineBansCount)
            .flatMapMany(t2 -> Flux.fromIterable(conf.getReplyToChannels())
                                   .flatMap(client::getChannelById)
                                   .filter(MessageChannel.class::isInstance)
                                   .cast(MessageChannel.class)
                                   .flatMap(ch -> ch.createEmbed(e -> showStats(e, t2.getT1(), t2.getT2(), conf))))
            .blockLast();
    }

    private void showStats(EmbedCreateSpec spec, BansDatabase.Stats stats, int offlineBans, Configuration conf) {
        spec.setTitle("Ban stats")
            .setColor(Color.VIVID_VIOLET)
            .addField("Added bans", Integer.toString(stats.numAdded()), true)
            .addField("Removed bans", Integer.toString(stats.numRemoved()), true)
            .addField("Offline bans", Integer.toString(offlineBans), true);

        if (offlineBans > 0)
            spec.setDescription(String.format("There are offline bans that need attention. " +
                                                      "Please use `%s list-bans` to see them.",
                                              conf.getPrefix()));
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

    @Command(header = "Chivalry Ban Manager%n")
    private static class Cmd {
    }

    public static class ChivalryServer {
        @Option(names = {"--host"}, required = true, description = "The chivalry server IP address.")
        public String hostname;

        @Option(names = {"--log-path"}, defaultValue = "/UDKGame/Config/PCServer-UDKGame.ini",
                showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
                description = "Path to the bans configuration file.")
        public String logPath;

        @Option(names = {"-u", "--user"}, required = true, description = "FTP access user name.")
        public String username;

        @Option(names = {"-p", "--password"}, required = true, description = "FTP access password.")
        public String password;
    }
}
