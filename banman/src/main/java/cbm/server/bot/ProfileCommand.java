package cbm.server.bot;

import cbm.server.Bot;
import cbm.server.SteamID;
import cbm.server.SteamWeb;
import cbm.server.TextUtils;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static cbm.server.SteamWeb.playerProfile;

@Command(name = "profile", header = "Show the canonical Steam profile URL", synopsisHeading = "%nUsage: ",
        description = {"%nUnlike the custom URL, the canonical one cannot be changed by the player.%n"})
public class ProfileCommand implements BotCommand {

    @Parameters(index = "0", paramLabel = "<id-or-url>",
            description = {"Steam ID or profile URL. The supported formats are:",
                    "- steamID (STEAM_0:0:61887661)",
                    "- steamID3 ([U:1:123775322])",
                    "- steamID64 (76561198084041050)",
                    "- full profile URL (https://steamcommunity.com/profiles/76561198084041050/)",
                    "- custom URL (robin-the-not-quite-so-brave)",
                    "- full custom URL (https://steamcommunity.com/id/robin-the-not-quite-so-brave)"})
    private String idOrUrl;

    @Override
    public @NotNull Flux<Message> executeFull(@NotNull Message message) {
        return Bot.resolveSteamID(idOrUrl)
                  .flatMap(s -> playerProfile(s.profileUrl())
                                        .flatMap(p -> message.getChannel()
                                                             .flatMap(ch -> ch.createEmbed(e -> embed(e, s, p)))))
                  .flux();
    }

    private void embed(EmbedCreateSpec spec, SteamID steamID, SteamWeb.Profile profile) {
        spec.setUrl(profile.getUrl())
            .setTitle(profile.getName())
            .setThumbnail(profile.getAvatar())
            .setColor(Color.of((int) steamID.steamID64()))
            .addField("Steam ID64", "`" + steamID.steamID64() + "`", true)
            .addField("Steam ID", "`" + steamID.steamID() + "`", true);

        Optional.ofNullable(profile.getName())
                .map(TextUtils::printable)
                .filter(s -> !s.isBlank())
                .filter(s -> !s.equals(profile.getName()))
                .ifPresent(s -> spec.addField("Readable name", s, false));
    }
}
