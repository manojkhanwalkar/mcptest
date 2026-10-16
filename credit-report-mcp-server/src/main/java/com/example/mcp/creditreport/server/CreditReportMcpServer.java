package com.example.mcp.creditreport.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Entry point: an MCP server over stdio that serves credit reports from the file system.
 *
 * <pre>
 *   MCP client --(JSON-RPC over stdin/stdout)--> this server --(file I/O)--> CREDIT_REPORTS_DIR/*.md
 * </pre>
 *
 * <p>Configuration: the {@code CREDIT_REPORTS_DIR} environment variable. Without it the server
 * uses {@code test-data/credit-reports}, looked up from the current directory and its parent so
 * it works from the repository root or a module directory. stdout carries the MCP protocol, so
 * nothing else may print to it; logging goes to stderr.
 *
 * <p>Run with {@code java -jar credit-report-mcp-server/target/credit-report-mcp-server.jar}.
 * Normally an MCP client (credit-report-mcp-client, Claude Code, ...) launches it rather than a person.
 */
public class CreditReportMcpServer {

    static final String DEFAULT_DIR = "test-data/credit-reports";

    public static void main(String[] args) {
        Path directory = reportsDirectory();
        McpJsonMapper json = McpJsonDefaults.getMapper();
        CreditReportStore store = new CreditReportStore(directory);
        CreditReportTools tools = new CreditReportTools(store, json);

        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
                .serverInfo("credit-report-mcp-server", "0.0.1")
                .instructions("Tools for reading fictitious test credit reports stored in " + store.directory()
                        + ". Call list_credit_reports to find a report number, then get_credit_report for its Markdown text.")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();

        // The stdio transport reads stdin on its own threads; stop cleanly when the client goes away.
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    }

    private static Path reportsDirectory() {
        String configured = System.getenv("CREDIT_REPORTS_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Stream.of(DEFAULT_DIR, "../" + DEFAULT_DIR)
                .map(Path::of)
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(Path.of(DEFAULT_DIR));
    }
}
