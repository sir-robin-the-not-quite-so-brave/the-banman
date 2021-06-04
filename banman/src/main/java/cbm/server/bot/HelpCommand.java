package cbm.server.bot;

import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.IHelpCommandInitializable2;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;

@Command(name = "help", header = "Displays help information about the specified command",
        synopsisHeading = "%nUsage: ", helpCommand = true,
        description = {"%nWhen no COMMAND is given, the usage help for the main command is displayed.",
                "If a COMMAND is specified, the help for that command is shown.%n"})
public class HelpCommand implements BotCommand, IHelpCommandInitializable2 {

    @Parameters(paramLabel = "COMMAND", descriptionKey = "helpCommand.command",
            description = "The COMMAND to display the usage help message for.")
    private String[] commands = new String[0];

    private CommandLine parent;
    private Help.ColorScheme colorScheme;

    @Override
    public void init(CommandLine helpCommandLine, Help.ColorScheme colorScheme, PrintWriter out, PrintWriter err) {
        this.parent = Objects.requireNonNull(helpCommandLine, "helpCommandLine");
        this.colorScheme = Objects.requireNonNull(colorScheme, "colorScheme");
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
        final StringWriter sw = new StringWriter();
        try (final PrintWriter out = new PrintWriter(sw)) {
            command.usage(out, colorScheme);
        }
        final String help = sw.toString();
        return Flux.just(help);
    }
}
