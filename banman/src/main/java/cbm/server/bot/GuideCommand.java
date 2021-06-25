package cbm.server.bot;

import discord4j.core.object.entity.Message;
import discord4j.rest.util.Color;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import reactor.core.publisher.Flux;

@Command(name = "guide", header = "User guide", synopsisHeading = "%nUsage: ",
        description = {"%nGive a link to the user guide.%n"})
public class GuideCommand implements BotCommand {

    private final String guideUrl;

    public GuideCommand(String guideUrl) {
        this.guideUrl = guideUrl;
    }

    @Override
    public @NotNull Flux<Message> executeFull(@NotNull Message message) {

        return message.getChannel()
                      .flatMap(ch -> ch.createEmbed(em -> em.setTitle("User guide")
                                                            .setUrl(guideUrl)
                                                            .setColor(Color.JAZZBERRY_JAM)))
                      .flux();
    }
}
