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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    public @NotNull Flux<String> execute(String params, Message message) {
        return message.getMessageReference()
                      .flatMap(MessageReference::getMessageId)
                      .map(id -> nextPage(params, message.getChannel(), id))
                      .orElseGet(() -> bansDatabase.searchBans(new SearchRequest(params))
                                                   .flatMapIterable(response -> resultsToStrings(params, response)));
    }

    @NotNull
    private Flux<String> nextPage(String params, Mono<MessageChannel> channel, Snowflake id) {
        return channel.flatMapMany(ch -> ch.getMessageById(id)
                                           .flatMapMany(msg -> {
                                               final String content = msg.getContent();
                                               final SearchRequest request = nextRequest(content);
                                               if (request == null)
                                                   return Flux.just("Error");
                                               return bansDatabase.searchBans(request)
                                                                  .flatMapIterable(response ->
                                                                                           resultsToStrings(params,
                                                                                                            response));
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

    private Iterable<String> resultsToStrings(String queryString, SearchResponse<Ban> response) {
        final String prefix = String.format("**Results (%d - %d of %d):**\n```\n",
                                            response.getFrom() + 1, response.getTo(), response.getTotal());
        final String continueAfter = response.getContinueAfter();
        final String suffix =
                continueAfter != null ? String.format("```\n||>%s %s<||", continueAfter, queryString) : "```";
        final int suffixLen = suffix.getBytes(StandardCharsets.UTF_8).length;

        StringBuilder sb = new StringBuilder(prefix);
        int currentLen = prefix.getBytes(StandardCharsets.UTF_8).length;

        final List<String> responses = new ArrayList<>();
        @NotNull List<Ban> results = response.results;
        final int count = results.size();
        for (int i = 0; i < count; i++) {
            final String banString = results.get(i).toString();
            final int banLen = banString.getBytes(StandardCharsets.UTF_8).length + 1;
            final int closeLen = i == count - 1 ? suffixLen : 4;
            if (currentLen + banLen + closeLen >= 2000) {
                sb.append("```\n");
                responses.add(sb.toString());
                sb = new StringBuilder("```\n");
                currentLen = 4;
            }
            sb.append(banString).append('\n');
            currentLen += banLen;
        }

        sb.append(suffix);
        responses.add(sb.toString());

        return responses;
    }
}
