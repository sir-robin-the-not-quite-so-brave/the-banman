package cbm.server.bot;

import cbm.server.db.BansDatabase;
import cbm.server.db.SearchRequest;
import cbm.server.db.SearchResponse;
import cbm.server.model.Ban;
import discord4j.common.util.Snowflake;
import discord4j.core.object.MessageReference;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

public class SearchCommand implements BotCommand {
    private static final String[] DESCRIPTION = new String[]{
            "*query* - Search bans."
    };

    private final BansDatabase bansDatabase;

    public SearchCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull String name() {
        return "search";
    }

    @Override
    public @NotNull String[] description() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull Mono<String> execute(String params, Message message) {
        return message.getMessageReference()
                      .flatMap(MessageReference::getMessageId)
                      .map(id -> nextPage(params, message.getChannel(), id))
                      .orElseGet(() -> bansDatabase.searchBans(new SearchRequest(params))
                                                   .map(response -> resultsToString(params, response)));
    }

    @NotNull
    private Mono<String> nextPage(String params, Mono<MessageChannel> channel, Snowflake id) {
        return channel.flatMap(ch -> ch.getMessageById(id)
                                       .flatMap(msg -> {
                                           final String content = msg.getContent();
                                           final SearchRequest request = nextRequest(content);
                                           if (request == null)
                                               return Mono.just("Error");
                                           return bansDatabase.searchBans(request)
                                                              .map(response -> resultsToString(params, response));
                                       }));
    }

    private SearchRequest nextRequest(String content) {
        if (!content.endsWith("<||"))
            return null;
        final int idx = content.lastIndexOf("||>");
        if (idx == -1)
            return null;

        final String next = content.substring(idx + 3, content.length() - 3);
        final int idx2 = next.indexOf(' ');
        if (idx2 == -1)
            return null;
        final String continueFrom = next.substring(0, idx2);
        final String queryString = next.substring(idx2 + 1);
        return new SearchRequest(queryString, continueFrom);
    }

    private String resultsToString(String queryString, SearchResponse<Ban> response) {
        final String prefix = String.format("**Results (%d - %d of %d):**\n```\n",
                                            response.getFrom() + 1, response.getTo(), response.getTotal());
        final String continueAfter = response.getContinueAfter();
        final String suffix =
                continueAfter != null ? String.format("```\n||>%s %s<||", continueAfter, queryString) : "```";
        return response.results
                       .stream()
                       .map(Ban::toString)
                       .collect(Collectors.joining("\n", prefix, suffix));
    }
}
