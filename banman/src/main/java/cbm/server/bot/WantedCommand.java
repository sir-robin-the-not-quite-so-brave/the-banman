package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.db.BansDatabase;
import cbm.server.model.Mention;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Command(name = "wanted", header = "List player mentions in the watch-list channels", synopsisHeading = "%nUsage: ",
        description = {"%nShow links to mentions for the given player in the watch-list channel.%n"})
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
    public @NotNull Flux<String> execute(@NotNull Message message) {
        return Bot.resolveSteamID(idOrUrl)
                  .flatMapMany(bansDatabase::findMentions)
                  .collectList()
                  .flatMapMany(mentions -> {
                      if (mentions.isEmpty())
                          return Flux.just("**No mentions found.**");
                      final List<String> links = mentions.stream()
                                                           .map(this::toLink)
                                                           .collect(Collectors.toList());
                      final MessageComposer composer =
                              new MessageComposer.Builder()
                                      .setHeader("**Mentions found:**")
                                      .build();
                      return Flux.fromIterable(composer.compose(links));
                  });
    }

    private String toLink(Mention mention) {
        final String guildId =
                Optional.ofNullable(mention.getGuildId())
                        .map(Snowflake::asString)
                        .orElse("");

        return String.format("%s - https://discord.com/channels/%s/%s/%s",
                             mention.getMentionedAt(),
                             guildId,
                             mention.getChannelId().asString(),
                             mention.getMessageId().asString());
    }
}
