package cbm.server;

import org.jetbrains.annotations.NotNull;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    public static <T> Mono<T> asyncOne(Callable<T> callable) {
        return Mono.defer(() -> Mono.fromCallable(callable)
                                    .subscribeOn(Schedulers.elastic()));
    }

    public static <T> Flux<T> asyncMany(Supplier<? extends Iterable<T>> supplier) {
        return Flux.defer(() -> Flux.fromIterable(supplier.get())
                                    .subscribeOn(Schedulers.elastic()));
    }

    @SuppressWarnings("unused")
    public static <T> @NotNull Set<T> union(@NotNull Set<T> s1, @NotNull Set<T> s2) {
        return Stream.concat(s1.stream(), s2.stream())
                     .collect(Collectors.toSet());
    }

    public static <T> @NotNull Set<T> intersection(@NotNull Set<T> s1, @NotNull Set<T> s2) {
        return s1.stream()
                 .filter(s2::contains)
                 .collect(Collectors.toSet());
    }

    public static @NotNull List<String> extractUrls(@NotNull String content) {
        final List<String> urls = new ArrayList<>();

        final var linkSpans = LinkExtractor.builder()
                                           .linkTypes(EnumSet.of(LinkType.URL))
                                           .build()
                                           .extractLinks(content);
        for (var linkSpan : linkSpans) {
            final String link = content.substring(linkSpan.getBeginIndex(), linkSpan.getEndIndex());
            urls.add(link);
        }

        return urls;
    }
}
