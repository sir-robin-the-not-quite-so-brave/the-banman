package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.model.Mention;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.util.Color;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Command(name = "wanted", header = "List player mentions in the watch-list channels", synopsisHeading = "%nUsage: ",
        description = {"%nShow links to mentions for the given player in the watch-list channels.%n"})
public class WantedCommand implements BotCommand {

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

    public WantedCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<Message> executeFull(@NotNull Message message) {
        return message.getChannel()
                      .flatMapMany(channel -> Bot.resolveSteamID(idOrUrl)
                                                 .flatMapMany(bansDatabase::findMentions)
                                                 .collectList()
                                                 .flatMapMany(mentions -> toMessages(channel, mentions)));
    }

    private Publisher<Message> toMessages(MessageChannel channel, List<Mention> mentions) {
        if (mentions.isEmpty())
            return channel.createMessage("**No mentions found.**");

        return Flux.fromIterable(mentions)
                   .flatMap(mention -> message(channel, mention));
    }

    private Mono<Message> message(MessageChannel channel, Mention mention) {
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

    private Mono<Message> message(MessageChannel channel, Mention mention, TextChannel mentionChannel, Member author,
                                  Color color) {

        return channel.createEmbed(e -> e.setColor(color)
                                         .setTitle("Jump to the mention")
                                         .setDescription("Mentioned in **#" + mentionChannel.getName() + "**")
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
