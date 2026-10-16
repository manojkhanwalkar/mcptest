package com.example.mcp.creditreport.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP client for credit-report-mcp-server. It launches the server jar as a subprocess, speaks MCP
 * to it over stdin/stdout, and returns the reports it serves.
 *
 * <pre>
 *   this client --(MCP over stdio)--> credit-report-mcp-server --(file I/O)--> credit report files
 * </pre>
 * The client never reads the report files itself; everything goes through MCP tools.
 */
public class CreditReportMcpClient implements AutoCloseable {

    /** Thrown when a tool call returns {@code isError: true}, e.g. an unknown report number. */
    public static class ToolCallException extends RuntimeException {
        public ToolCallException(String tool, String message) {
            super(tool + " failed: " + message);
        }
    }

    /** One entry of {@code list_credit_reports}. {@code preparedFor} and {@code dateGenerated} may be null. */
    public record ReportSummary(String reportNumber, String preparedFor, String dateGenerated,
                                String fileName, long sizeBytes) {
    }

    private static final TypeRef<List<ReportSummary>> REPORT_LIST = new TypeRef<>() {
    };

    private final McpSyncClient client;
    private final McpJsonMapper json = McpJsonDefaults.getMapper();

    /**
     * Starts the server and performs the MCP initialize handshake.
     *
     * @param serverJar  path to credit-report-mcp-server.jar
     * @param reportsDir directory holding the reports, passed to the server as
     *                   {@code CREDIT_REPORTS_DIR}; null to use the server's default
     */
    public CreditReportMcpClient(String serverJar, String reportsDir) {
        this(serverParameters(serverJar, reportsDir));
    }

    /**
     * Starts any stdio MCP server described by {@code params} and performs the initialize
     * handshake. Tests use this to launch a fake server.
     */
    public CreditReportMcpClient(ServerParameters params) {
        StdioClientTransport transport = new StdioClientTransport(params, json);
        // The server's stderr (its logs) is not part of the protocol; surface it on ours.
        transport.setStdErrorHandler(line -> System.err.println("[credit-report-mcp-server] " + line));

        this.client = McpClient.sync(transport)
                .clientInfo(new Implementation("credit-report-mcp-client", "0.0.1"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
    }

    private static ServerParameters serverParameters(String serverJar, String reportsDir) {
        // Launch the server with the same JVM that runs this client.
        ServerParameters.Builder builder = ServerParameters.builder(ProcessHandle.current().info().command().orElse("java"))
                .args("-jar", serverJar);
        if (reportsDir != null) {
            builder.addEnvVar("CREDIT_REPORTS_DIR", reportsDir);
        }
        return builder.build();
    }

    /** Name and version the server reported during initialize. */
    public Implementation serverInfo() {
        return client.getServerInfo();
    }

    /** The reports the server can serve, sorted by report number. */
    public List<ReportSummary> listReports() {
        String text = call("list_credit_reports", Map.of());
        try {
            return json.readValue(text, REPORT_LIST);
        } catch (IOException e) {
            throw new ToolCallException("list_credit_reports", "unexpected response: " + text);
        }
    }

    /** The full Markdown text of one report. */
    public String getReport(String reportNumber) {
        return call("get_credit_report", Map.of("reportNumber", reportNumber));
    }

    /**
     * Calls a tool and returns its text result.
     *
     * @throws ToolCallException if the server reports an error
     */
    String call(String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(new CallToolRequest(tool, arguments));
        String text = result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new ToolCallException(tool, text);
        }
        return text;
    }

    /** Closes the MCP session, which also stops the server subprocess. */
    @Override
    public void close() {
        client.closeGracefully();
    }
}
