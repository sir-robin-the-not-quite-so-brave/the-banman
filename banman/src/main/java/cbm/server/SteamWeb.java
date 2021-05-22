package cbm.server;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class SteamWeb {

    public static @NotNull Profile downloadProfile(@NotNull String profileUrl) throws IOException {
        final var doc = Jsoup.connect(profileUrl).get();
        final var name =
                doc.select(".actual_persona_name").stream()
                   .map(Element::text)
                   .findFirst()
                   .orElse(null);

        final var avatar =
                doc.select(".playerAvatarAutoSizeInner > img").stream()
                   .map(element -> element.attr("src"))
                   .findFirst()
                   .orElse(null);

        return new Profile() {
            @Override
            public String getUrl() {
                return profileUrl;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getAvatar() {
                return avatar;
            }
        };
    }

    public static Optional<String> resolveId(@NotNull String s) throws IOException {
        final var doc = Jsoup.connect("https://steamid.io/lookup")
                             .data("input", s)
                             .post();

        final var element = doc.selectFirst(new Evaluator() {

            final AtomicBoolean next = new AtomicBoolean(false);

            @Override
            public boolean matches(Element root, Element element) {
                return next.getAndSet(element.tag().normalName().equals("dt")
                                              && element.text().equals("steamID64"));
            }
        });

        return Optional.ofNullable(element)
                       .map(Element::text);
    }

    public static Mono<Profile> playerProfile(@NotNull String profileUrl) {
        return Mono.defer(() -> Mono.fromCallable(() -> downloadProfile(profileUrl))
                                    .subscribeOn(Schedulers.elastic()));
    }

    public static Mono<SteamID> resolveSteamID(@NotNull String s) {
        final Callable<SteamID> steamIDResolver =
                () -> resolveId(s).flatMap(SteamID::steamID)
                                  .orElseThrow(() -> new IllegalArgumentException("Cannot resolve steam ID: " + s));

        return Mono.defer(() -> Mono.fromCallable(steamIDResolver)
                                    .subscribeOn(Schedulers.elastic()));
    }

    @SuppressWarnings("unused")
    public interface Profile {
        String getUrl();

        String getName();

        String getAvatar();
    }
}
