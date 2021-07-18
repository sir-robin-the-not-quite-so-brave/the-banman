package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.Utils;
import cbm.server.db.BansDatabase;
import cbm.server.db.BansDatabase.BanLogEntry;
import cbm.server.model.Ban;
import cbm.server.model.Mention;
import cbm.server.model.OfflineBan;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.util.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Command(name = "info", header = "List player bans and mentions in the watch-list channels",
        synopsisHeading = "%nUsage: ",
        description = {"%nShow bans and links to mentions for the given player in the watch-list channels.%n"})
public class InfoCommand implements BotCommand {

    private static final Logger LOGGER = LogManager.getLogger();

    @Option(names = "-a", description = "Show all bans")
    private boolean showAllBans;

    @Parameters(index = "0", paramLabel = "<id-or-url>",
            description = {"Steam ID or profile URL. The supported formats are:",
                    "- steamID (STEAM_0:0:61887661)",
                    "- steamID3 ([U:1:123775322])",
                    "- steamID64 (76561198084041050)",
                    "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
                    "- custom URL (robin-the-not-quite-so-brave)",
                    "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"})
    private String idOrUrl;

    private final BansDatabase bansDatabase;

    public InfoCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<Message> executeFull(@NotNull Message message) {
        return message.getChannel()
                      .flatMapMany(ch -> Bot.resolveSteamID(idOrUrl)
                                            .flatMapMany(steamID -> {
                                                final var banHistory =
                                                        bansDatabase.getBanHistory(steamID)
                                                                    .buffer(2)
                                                                    .map(this::combine);

                                                final var mentions =
                                                        bansDatabase.findMentions(steamID)
                                                                    .map(MentionInfo::new);

                                                final var offlineBans =
                                                        bansDatabase.getOfflineBans()
                                                                    .filter(b -> Objects.equals(b.getId(),
                                                                                                steamID.s64()))
                                                                    .map(OfflineBanInfo::new);

                                                return Flux.merge(banHistory, mentions, offlineBans)
                                                           .sort()
                                                           .doOnNext(info -> LOGGER.debug("message: {} - {}",
                                                                                          info.sortBy(),
                                                                                          info.getClass()))
                                                           .flatMap(e -> e.toMessage(ch))
                                                           .switchIfEmpty(ch.createMessage("No bans or mentions"));
                                            }));
    }

    private Info combine(List<BanLogEntry> banLogEntries) {
        assert banLogEntries.size() == 1 || banLogEntries.size() == 2;
        final BanLogEntry start = banLogEntries.get(0);
        final BanLogEntry end = banLogEntries.size() == 2 ? banLogEntries.get(1) : null;
        return new BanInfo(start, end);
    }

    private static String toString(@Nullable Instant instant) {
        if (instant == null)
            return "Forever";

        final ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(zonedDateTime);
    }

    private static Instant bannedUtil(Ban ban) {
        if (ban.isNetIDBan() || ban.getDuration().isZero() || ban.getDuration().isNegative())
            return null;

        return ban.getEnactedTime().plus(ban.getDuration());
    }

    private interface Info extends Comparable<Info> {
        @NotNull Instant sortBy();

        Mono<Message> toMessage(MessageChannel channel);

        @Override
        default int compareTo(@NotNull Info o) {
            return sortBy().compareTo(o.sortBy());
        }
    }

    private static class BanInfo implements Info {
        public final @NotNull Instant added;
        private final @Nullable Instant bannedUntil;
        public final @Nullable Instant removed;
        public final @NotNull Duration expectedDuration;
        public final @NotNull String reason;
        public final @Nullable String playerName;
        private final @Nullable String ip;

        public BanInfo(@NotNull BanLogEntry start, @Nullable BanLogEntry end) {
            added = Optional.ofNullable(start.getBan().getEnactedTime()).orElseGet(start::getDetectedAt);
            bannedUntil = bannedUtil(start.getBan());
            removed = Optional.ofNullable(end).map(BanLogEntry::getDetectedAt).orElse(null);
            expectedDuration = Optional.ofNullable(start.getBan().getDuration()).orElse(Duration.ZERO);
            reason = Optional.ofNullable(start.getBan().getReason()).orElse("NetID ban.");
            playerName = start.getBan().getPlayerName();
            ip = Optional.ofNullable(start.getBan().getIpPolicy())
                         .filter(s -> s.startsWith("DENY,"))
                         .map(s -> s.substring("DENY,".length()))
                         .filter(s -> !s.equals("0.0.0.0"))
                         .orElse(null);
        }

        @Override
        public @NotNull Instant sortBy() {
            return added;
        }

        @Override
        public Mono<Message> toMessage(MessageChannel channel) {
            return channel.createEmbed(spec -> {
                if (playerName != null)
                    spec.addField("Name", playerName, true);

                if (ip != null)
                    spec.addField("IP Address", ip, true);

                spec.addField("Reason", reason, false)
                    .setTimestamp(added)
                    .addField("From", InfoCommand.toString(added), true)
                    .addField("Until", InfoCommand.toString(bannedUntil), true);

                if (removed == null) {
                    spec.setTitle("Current Ban")
                        .setColor(Color.RED)
                        .addField("Duration", expectedDuration.toString(), true);
                } else {
                    final Duration actualDuration = Duration.between(added, removed);
                    if (Utils.compare(expectedDuration, actualDuration) <= 0) {
                        spec.setTitle("Fully Served Ban")
                            .setColor(Color.ORANGE)
                            .addField("Duration", expectedDuration.toString(), true);
                    } else {
                        spec.setTitle("Shortened Ban")
                            .setColor(Color.DARK_GOLDENROD)
                            .addField("Removed at", InfoCommand.toString(removed), true)
                            .addField("Original Duration", expectedDuration.toString(), true)
                            .addField("Actual Duration", actualDuration.toString(), true);
                    }
                }
            });
        }
    }

    private static class MentionInfo implements Info {

        private final @NotNull Mention mention;

        private MentionInfo(@NotNull Mention mention) {
            this.mention = mention;
        }

        @Override
        public @NotNull Instant sortBy() {
            return mention.getMentionedAt();
        }

        @Override
        public Mono<Message> toMessage(MessageChannel channel) {
            final Snowflake guildId = mention.getGuildId();
            return channel.getClient()
                          .getGuildById(guildId)
                          .flatMap(guild -> guild.getChannelById(mention.getChannelId()))
                          .filter(TextChannel.class::isInstance)
                          .cast(TextChannel.class)
                          .flatMap(ch -> message(channel, mention, ch));
        }

        @NotNull
        private Mono<Message> message(MessageChannel channel, Mention mention, TextChannel mentionChannel) {
            return mentionChannel.getMessageById(mention.getMessageId())
                                 .flatMap(Message::getAuthorAsMember)
                                 .flatMap(author -> author.getColor()
                                                          .flatMap(color -> message(channel, mention, mentionChannel,
                                                                                    author, color)));
        }

        private Mono<Message> message(MessageChannel channel, Mention mention, TextChannel mentionChannel,
                                      Member author, Color color) {

            return channel.createEmbed(e -> e.setColor(color)
                                             .setTitle("Mention")
                                             .addField("Channel", "#" + mentionChannel.getName(), true)
                                             .setUrl(toLink(mention))
                                             .setAuthor(author.getDisplayName(), null, author.getAvatarUrl())
                                             .setTimestamp(mention.getMentionedAt()));
        }

        private String toLink(Mention mention) {
            return String.format("https://discord.com/channels/%s/%s/%s",
                                 mention.getGuildId().asString(),
                                 mention.getChannelId().asString(),
                                 mention.getMessageId().asString());
        }
    }

    private static class OfflineBanInfo implements Info {

        private final @NotNull OfflineBan offlineBan;
        private final @NotNull Instant enactedTime;

        private OfflineBanInfo(@NotNull OfflineBan offlineBan) {
            this.offlineBan = offlineBan;
            this.enactedTime = Optional.ofNullable(offlineBan.getEnactedTime())
                                       .orElseGet(Instant::now);
        }

        @Override
        public @NotNull Instant sortBy() {
            return enactedTime;
        }

        @Override
        public Mono<Message> toMessage(MessageChannel channel) {
            return channel.createEmbed(spec -> {
                spec.setTitle("Pending Offline Ban")
                    .setColor(Color.HOKI)
                    .setTimestamp(enactedTime);

                if (offlineBan.getPlayerName() != null)
                    spec.addField("Name", offlineBan.getPlayerName(), false);

                if (offlineBan.getReason() != null)
                    spec.addField("Reason", offlineBan.getReason(), false);

                if (offlineBan.getDuration() != null)
                    spec.addField("Duration", offlineBan.getDuration().toString(), true);
            });
        }
    }
}
