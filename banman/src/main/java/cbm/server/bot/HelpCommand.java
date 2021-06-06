package cbm.server.bot;

import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

@Command(name = "help", header = "Displays help information about the specified command",
        synopsisHeading = "%nUsage: ", helpCommand = true,
        description = {"%nWhen no COMMAND is given, the usage help for the bot is displayed.",
                "If a COMMAND is specified, the help for that command is shown.%n"})
public class HelpCommand implements BotCommand {

    private static final MessageComposer COMPOSER = new MessageComposer.Builder()
                                                            .setHeader("```")
                                                            .setFooter("```")
                                                            .build();

    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(paramLabel = "COMMAND", descriptionKey = "helpCommand.command",
            description = "The COMMAND to display the usage help message for.")
    private String[] commands = new String[0];

    private CommandLine parent;

    public void init(CommandLine helpCommandLine) {
        this.parent = Objects.requireNonNull(helpCommandLine, "helpCommandLine");
    }

    @Override
    public @NotNull Flux<String> execute(@NotNull Message message) {
        if (commands.length > 0) {
            final Map<String, CommandLine> parentSubcommands = parent.getCommandSpec().subcommands();
            final String fullName = commands[0];
            final CommandLine subcommand = parentSubcommands.get(fullName);
            if (subcommand == null)
                throw new ParameterException(parent,
                                             "Unknown subcommand '" + fullName + "'.",
                                             null,
                                             fullName);

            return usage(subcommand);
        } else {
            return usage(parent);
        }
    }

    private Flux<String> usage(CommandLine command) {
        final String usageMessage = command.getUsageMessage(Help.Ansi.OFF);
        final String[] lines = usageMessage.split("\n");
        return Flux.fromIterable(COMPOSER.compose(lines));
    }
}
