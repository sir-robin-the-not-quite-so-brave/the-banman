package cbm.server.bot;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "color", header = "Color reply", synopsisHeading = "%nUsage: ", hidden = true,
        description = {"%nReply with the selected color.%n"})
public class ColorCommand implements BotCommand {

    private static final Map<String, Color> COLORS;
    private static final String[] ALL_COLOR_NAMES;

    static {
        try {
            final Map<String, Color> colorMap = new LinkedHashMap<>();
            for (var field : Color.class.getDeclaredFields())
                if (Modifier.isStatic(field.getModifiers()) && Color.class.isAssignableFrom(field.getType()))
                    colorMap.put(field.getName(), (Color) field.get(null));
            COLORS = Collections.unmodifiableMap(colorMap);
            ALL_COLOR_NAMES = COLORS.keySet().toArray(new String[0]);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Parameters
    private String[] colors;

    @Override
    public @NotNull Flux<Message> executeFull(@NotNull Message message) {
        if (colors == null)
            colors = ALL_COLOR_NAMES;
        return message.getChannel()
                      .flatMapMany(channel -> Flux.just(colors)
                                                  .flatMap(color -> colorReply(channel, color)));
    }

    private Mono<Message> colorReply(MessageChannel channel, String colorName) {
        final Color color = COLORS.get(colorName);
        return channel.createEmbed(e -> {
            e.setTitle(colorName)
             .setUrl("https://duckduckgo.com");
            if (color == null)
                e.setDescription("Unknown color *" + colorName + "*");
            else
                e.setColor(color)
                 .addField("red", "`" + color.getRed() + "`", true)
                 .addField("green", "`" + color.getGreen() + "`", true)
                 .addField("blue", "`" + color.getBlue() + "`", true);
        });
    }
}
