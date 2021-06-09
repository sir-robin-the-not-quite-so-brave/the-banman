package cbm.server;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:^|[\\W])((ht|f)tp(s?)://|www\\.)"
                    + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+/?)*"
                    + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']*)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    public static <T> Mono<T> asyncOne(Callable<T> callable) {
        return Mono.defer(() -> Mono.fromCallable(callable)
                                    .subscribeOn(Schedulers.elastic()));
    }

    public static <T> Flux<T> asyncMany(Supplier<? extends Iterable<T>> supplier) {
        return Flux.defer(() -> Flux.fromIterable(supplier.get())
                                    .subscribeOn(Schedulers.elastic()));
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

    public static @NotNull List<String> extractUrls(@NotNull String content) {
        final List<String> urls = new ArrayList<>();

        final Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            final int matchStart = matcher.start(1);
            final int matchEnd = matcher.end();
            final String url = content.substring(matchStart, matchEnd);
            urls.add(url);
        }

        return urls;
    }
}
