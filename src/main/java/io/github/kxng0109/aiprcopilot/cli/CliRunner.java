package io.github.kxng0109.aiprcopilot.cli;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import picocli.CommandLine;

@Component
@RequiredArgsConstructor
public class CliRunner implements CommandLineRunner {

    private final AnalyzeCommand analyzeCommand;

    private final CommandLine.IFactory factory;

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "analyze".equals(args[0])) {
            String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

            int exitCode = new CommandLine(analyzeCommand, factory).execute(commandArgs);

            System.exit(exitCode);
        }
    }
}
