package cbm.server;

import cbm.server.db.BansDatabase;
import cbm.server.model.Mention;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ChannelWatcher {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Pattern STEAM_URL_RX =
            Pattern.compile("https?://steamcommunity\\.com/(?:profiles|id)/.+", Pattern.CASE_INSENSITIVE);

    private final MessageChannel channel;
    private final BansDatabase bansDatabase;
    private final String channelName;
    private final Snowflake guildId;

    public ChannelWatcher(MessageChannel channel, BansDatabase bansDatabase) {
        this.channel = channel;
        this.bansDatabase = bansDatabase;
        if (channel instanceof GuildChannel) {
            final GuildChannel guildChannel = (GuildChannel) channel;
            this.channelName = guildChannel.getName();
            this.guildId = guildChannel.getGuildId();
        } else {
            this.channelName = channel.getId().toString();
            this.guildId = null;
        }
    }

    public Mono<Stats> updateChannel() {
        return Mono.justOrEmpty(channel.getLastMessageId())
                   .flatMap(this::updateChannel);
    }

    public Mono<Stats> updateChannel(@NotNull Snowflake latestMessageId) {
        return bansDatabase.setLastProcessedMessageId(channel.getId(), latestMessageId,
                                                      lastId -> lastId == null
                                                                        || lastId.compareTo(latestMessageId) < 0)
                           .flatMap(o -> process(latestMessageId, o.orElse(null)));
    }

    private Mono<Stats> process(@NotNull Snowflake from, @Nullable Snowflake to) {
        LOGGER.info("Reading channel {} messages from {} back to {}", channelName, from, to);
        final Stats stats = new Stats();
        return channel.getMessageById(from)
                      .concatWith(channel.getMessagesBefore(from))
                      .takeWhile(to != null ? message -> message.getId().compareTo(to) >= 0
                                            : message -> true)
                      .take(1000)
                      .doOnNext(message -> stats.messagesCount.incrementAndGet())
                      .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(true))
                      .flatMap(message -> {
                          final List<String> urls = Utils.extractUrls(message.getContent());
                          LOGGER.debug("[{}:{}] Found {} URLs. Message: {}", channelName, message.getId(),
                                       urls.size(), message.getContent());
                          return Flux.fromIterable(urls)
                                     .filter(ChannelWatcher::isProfileUrl)
                                     .flatMap(ChannelWatcher::resolveSteamID)
                                     .doOnNext(steamID -> stats.mentionsCount.incrementAndGet())
                                     .map(steamID -> new Mention(steamID, guildId, message))
                                     .flatMap(bansDatabase::addMention);
                      })
                      .then(Mono.just(stats));
    }

    public static Mono<SteamID> resolveSteamID(String id) {
        return Bot.resolveSteamID(id)
                  .onErrorResume(t -> {
                      if (t instanceof IllegalArgumentException)
                          LOGGER.info("Failed to resolve {}", id);
                      else
                          LOGGER.warn("Failed to resolve " + id, t);
                      return Mono.empty();
                  });
    }

    static boolean isProfileUrl(@NotNull String url) {
        return STEAM_URL_RX.matcher(url).matches();
    }

    public static class Stats {
        public final AtomicInteger messagesCount = new AtomicInteger();
        public final AtomicInteger mentionsCount = new AtomicInteger();

        @Override
        public String toString() {
            return new StringJoiner(", ", Stats.class.getSimpleName() + "[", "]")
                           .add("messagesCount=" + messagesCount)
                           .add("mentionsCount=" + mentionsCount)
                           .toString();
        }
    }
}
