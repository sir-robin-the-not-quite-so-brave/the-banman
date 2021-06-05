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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Command(name = "search", header = "Search the bans database", synopsisHeading = "%nUsage: ",
        description = {"%nFull-text search the bans database.",
                "To get more results you can reply to the latest response with the command 'search'.%n"})
public class SearchCommand implements BotCommand {

    @Option(names = "-t", description = "Show a query tutorial")
    private boolean showTutorial;

    @Parameters(arity = "0..*",
            description = {"Some examples:",
                    "'cookie' - search for the word \"cookie\" in the player names or in the ban reasons",
                    "'name:cookie' - search for the word \"cookie\" only in the player names",
                    "'reason:cookie' - search for the word \"cookie\" only in the reasons",
                    "'rob*' - search for words starting with \"rob\""})
    private String[] query;

    private final BansDatabase bansDatabase;

    public SearchCommand(BansDatabase bansDatabase) {
        this.bansDatabase = bansDatabase;
    }

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        if (showTutorial)
            return Flux.just("Search syntax tutorial: https://www.lucenetutorial.com/lucene-query-syntax.html");

        final String params = query != null ? String.join(" ", query) : "";
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
        final String header = String.format("**Results (%d - %d of %d):**",
                                            response.getFrom() + 1, response.getTo(), response.getTotal());
        final MessageComposer.Builder builder = new MessageComposer.Builder()
                                                        .setHeader(header)
                                                        .setPrefix("```")
                                                        .setSuffix("```");
        Optional.ofNullable(response.getContinueAfter())
                .map(ca -> String.format("||>%s %s<||", ca, queryString))
                .ifPresent(builder::setFooter);

        final MessageComposer composer = builder.build();
        final List<String> bans = response.results.stream()
                                                  .map(Ban::toString)
                                                  .collect(Collectors.toList());
        return composer.compose(bans);
    }
}
