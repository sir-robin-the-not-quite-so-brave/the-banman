package cbm.server;

import cbm.server.bot.BotCommand;
import cbm.server.bot.HelpCommand;
import cbm.server.bot.MessageComposer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.PrivateChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cbm.server.Utils.intersection;

public class MessageHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Logger COMMAND_LOGGER = LogManager.getLogger("command");

    /**
     * This cache stores recently replied to, unauthorized channels. We put the channel
     * in the cache when we reply to it, and do not reply again as long as the channel
     * is still in the cache.
     */
    private static final Cache<Snowflake, Boolean> UNAUTHORIZED_CHANNELS =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .build();

    private final String prefix;
    private final Set<Snowflake> channelIds;
    private final Map<Snowflake, Set<Snowflake>> guildRoles;
    private final Supplier<CommandLine> commandLineSupplier;

    public MessageHandler(@NotNull String prefix,
                          @NotNull Set<Snowflake> channelIds,
                          @NotNull Map<Snowflake, Set<Snowflake>> guildRoles,
                          @NotNull Supplier<CommandLine> commandLineSupplier) {

        this.prefix = prefix;
        this.channelIds = channelIds;
        this.guildRoles = guildRoles;
        this.commandLineSupplier = commandLineSupplier;
    }

    @NotNull
    private static Mono<Message> replyTo(Message message, String response) {
        final String rs;
        if (response.getBytes(StandardCharsets.UTF_8).length > MessageComposer.MAX_MESSAGE_LENGTH) {
            LOGGER.error("Response message too long!", new RuntimeException());
            rs = response.substring(0, MessageComposer.MAX_MESSAGE_LENGTH - 3) + "...";
        } else {
            rs = response;
        }

        return message.getChannel()
                      .flatMap(channel -> channel.createMessage(spec -> spec.setContent(rs)
                                                                            .setMessageReference(message.getId())));
    }

    private static Mono<Boolean> isMemberWithRole(User user, Snowflake guildId, Set<Snowflake> allowedRoles) {
        return user.asMember(guildId)
                   .map(Member::getRoleIds)
                   .map(roles -> !intersection(roles, allowedRoles).isEmpty());
    }

    public Flux<Message> handle(@NotNull Message message) {
        return requiresPrefix(message)
                       .flatMapMany(requiresPrefix -> {
                           try {
                               final String[] args = message.getContent().split("\\s+");
                               if (requiresPrefix && (args.length == 0 || !prefix.equalsIgnoreCase(args[0])))
                                   return Flux.empty();

                               COMMAND_LOGGER.info("{}", message.getContent());

                               final String[] withoutPrefix = removeOptionalPrefix(args);

                               final HelpCommand helpCommand = new HelpCommand();
                               final CommandLine commandLine =
                                       commandLineSupplier.get()
                                                          .addSubcommand(helpCommand)
                                                          .setCommandName(prefix);
                               final ParseResult parsed = commandLine.parseArgs(withoutPrefix);
                               final BotCommand command = getBotCommand(parsed)
                                                                  .orElse(helpCommand);
                               if (command instanceof HelpCommand)
                                   ((HelpCommand) command).init(parsed.commandSpec().commandLine());

                               return command.execute(message)
                                             .onErrorResume(t -> {
                                                 LOGGER.warn("Command handler failed: " + message.getContent(), t);
                                                 return Mono.just(Optional.ofNullable(t.getMessage())
                                                                          .filter(s -> !s.isBlank())
                                                                          .map(s -> "```\n" + s + "\n```")
                                                                          .orElse("*An error has occurred*"));
                                             })
                                             .flatMap(reply -> replyTo(message, reply));
                           } catch (ParameterException e) {
                               return replyTo(message, e.getMessage());
                           }
                       });
    }

    private Optional<BotCommand> getBotCommand(ParseResult parsed) {
        if (parsed == null)
            return Optional.empty();

        return getBotCommand(parsed.subcommand())
                       .or(() -> Optional.of(parsed.commandSpec().userObject())
                                         .filter(BotCommand.class::isInstance)
                                         .map(BotCommand.class::cast));
    }

    private String[] removeOptionalPrefix(String[] args) {
        if (args.length == 0 || !prefix.equalsIgnoreCase(args[0]))
            return args;

        return Arrays.copyOfRange(args, 1, args.length);
    }

    /**
     * @return Empty {@link Mono} if the message should be ignored, {@link Mono} containing {@code True} if the message
     * should start with {@link #prefix}, or {@code False} if it doesn't have to.
     */
    private Mono<Boolean> requiresPrefix(Message message) {
        final Optional<User> optAuthor = message.getAuthor();
        if (optAuthor.isEmpty())
            return Mono.empty();

        final User author = optAuthor.get();
        if (author.isBot())
            return Mono.empty();

        if (channelIds.contains(message.getChannelId()))
            return Mono.just(true);

        return message.getChannel()
                      .filter(ch -> ch instanceof PrivateChannel)
                      .flatMap(ch -> Mono.zip(guildRoles.entrySet()
                                                        .stream()
                                                        .map(e -> isMemberWithRole(author, e.getKey(), e.getValue())
                                                                          .onErrorReturn(false))
                                                        .collect(Collectors.toList()),
                                              objects -> Arrays.asList(objects).contains(Boolean.TRUE) ? false : null)
                                         .switchIfEmpty(((Supplier<Mono<Boolean>>) () -> {
                                             if (UNAUTHORIZED_CHANNELS.getIfPresent(ch.getId()) != null)
                                                 return Mono.empty();

                                             return ch.createMessage("You're not an admin. Shoo!")
                                                      .flatMap(msg -> {
                                                          UNAUTHORIZED_CHANNELS.put(ch.getId(), false);
                                                          LOGGER.info("I told {} ({}) to piss off",
                                                                      author.getUsername(), author.getId());
                                                          return Mono.empty();
                                                      });
                                         }).get()));
    }
}
