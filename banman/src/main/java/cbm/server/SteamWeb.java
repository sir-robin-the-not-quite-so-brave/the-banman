package cbm.server;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cbm.server.Utils.asyncOne;

public class SteamWeb {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final LoadingCache<String, Profile> PROFILES_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(SteamWeb::downloadProfile);

    private static final LoadingCache<String, SteamID> STEAM_ID_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build(s -> resolveId(s).flatMap(SteamID::steamID).orElse(null));

    private static @NotNull Profile downloadProfile(@NotNull String profileUrl) throws IOException {
        LOGGER.debug("Downloading profile: {}", profileUrl);
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

    private static Optional<String> resolveId(@NotNull String s) throws IOException {
        LOGGER.debug("Resolving: {}", s);
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
        return asyncOne(() -> PROFILES_CACHE.get(profileUrl));
    }

    public static Mono<SteamID> resolveSteamID(@NotNull String s) {
        return asyncOne(() -> Optional.ofNullable(STEAM_ID_CACHE.get(s))
                                      .orElseThrow(() -> new IllegalArgumentException("Cannot resolve steam ID: "
                                                                                              + s)));
    }

    @SuppressWarnings("unused")
    public interface Profile {
        String getUrl();

        String getName();

        String getAvatar();
    }
}
