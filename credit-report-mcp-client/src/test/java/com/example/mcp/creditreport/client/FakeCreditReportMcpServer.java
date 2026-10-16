package com.example.mcp.creditreport.client;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.Map;
import java.util.function.Function;

/**
 * Minimal stdio MCP server with canned credit reports, started as a subprocess by
 * {@link CreditReportMcpClientTest}. It lets the client be tested without credit-report-mcp-server.
 *
 * <ul>
 *   <li>{@code list_credit_reports}: two fixed reports</li>
 *   <li>{@code get_credit_report}: {@code # Report <number>} for a listed number, a tool error otherwise</li>
 * </ul>
 */
public class FakeCreditReportMcpServer {

    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    static final String LIST = """
            [{"reportNumber":"0001-0002-03","preparedFor":null,"dateGenerated":null,"fileName":"credit-report-0001-0002-03.md","sizeBytes":10},
             {"reportNumber":"2416-0737-69","preparedFor":"ELENA C MOCKRIDGE","dateGenerated":"10/15/2026","fileName":"credit-report-2416-0737-69.md","sizeBytes":4321}]""";

    public static void main(String[] args) {
        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(JSON))
                .serverInfo("fake-credit-report-server", "9.9.9")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(
                        tool("list_credit_reports", a -> result(LIST, false)),
                        tool("get_credit_report", a -> LIST.contains("\"" + a.get("reportNumber") + "\"")
                                ? result("# Report " + a.get("reportNumber"), false)
                                : result("No credit report with number " + a.get("reportNumber"), true)))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    }

    private static SyncToolSpecification tool(String name, Function<Map<String, Object>, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder().name(name).description("fake " + name)
                        .inputSchema(JSON, "{\"type\":\"object\"}").build())
                .callHandler((exchange, request) -> handler.apply(request.arguments()))
                .build();
    }

    private static CallToolResult result(String text, boolean isError) {
        return CallToolResult.builder().addTextContent(text).isError(isError).build();
    }
}
