package com.example.calculator.client;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Command line front end:
 * <pre>
 *   java -jar calculator-mcp-client.jar              # list tools, then run a demo of every operation
 *   java -jar calculator-mcp-client.jar add 7 5      # call one tool and print the result
 * </pre>
 * Spring {@code --name=value} options (e.g. {@code --calculator.server.jar=...}) may be mixed in.
 * Exits with 1 on bad usage or when the server reports an error.
 */
@Component
public class CalculatorCli implements ApplicationRunner, ExitCodeGenerator {

    private final CalculatorClient calculator;
    private int exitCode;

    public CalculatorCli(List<McpSyncClient> mcpClients) {
        this.calculator = new CalculatorClient(mcpClients.getFirst());
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> args = arguments.getNonOptionArgs();
        if (args.isEmpty()) {
            runDemo();
        } else if (args.size() == 3 && isNumber(args.get(1)) && isNumber(args.get(2))) {
            String result = calculator.calculate(args.get(0), Double.parseDouble(args.get(1)), Double.parseDouble(args.get(2)));
            System.out.println(result);
            exitCode = result.startsWith("Error:") ? 1 : 0;
        } else {
            System.err.println("Usage: java -jar calculator-mcp-client.jar [<operation> <a> <b>]");
            System.err.println("Operations: " + String.join(", ", calculator.listOperations()));
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void runDemo() {
        System.out.println("Tools offered by the server: " + String.join(", ", calculator.listOperations()));
        for (String op : List.of("add", "subtract", "multiply", "divide", "remainder")) {
            System.out.printf("%-9s 17, 5 = %s%n", op, calculator.calculate(op, 17, 5));
        }
        System.out.printf("%-9s 1, 0  = %s%n", "divide", calculator.calculate("divide", 1, 0));
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
