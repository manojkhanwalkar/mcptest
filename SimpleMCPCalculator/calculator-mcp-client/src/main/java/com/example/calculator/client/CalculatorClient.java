package com.example.calculator.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Thin wrapper that turns calculator operations into MCP {@code tools/call} requests. */
public class CalculatorClient {

    private final McpSyncClient client;

    public CalculatorClient(McpSyncClient client) {
        this.client = client;
    }

    /** Names of the tools the server advertises. */
    public List<String> listOperations() {
        return client.listTools().tools().stream().map(Tool::name).toList();
    }

    /**
     * Calls the tool named {@code operation} with operands {@code a} and {@code b} and returns the
     * server's text result, or {@code "Error: <message>"} if the tool reported an error.
     * Spring AI repeats the exception message on several lines in error results, so only the
     * first line is kept.
     */
    public String calculate(String operation, double a, double b) {
        CallToolResult result = client.callTool(new CallToolRequest(operation, Map.of("a", a, "b", b)));
        String text = result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
        return Boolean.TRUE.equals(result.isError()) ? "Error: " + text.lines().findFirst().orElse("") : text;
    }
}
