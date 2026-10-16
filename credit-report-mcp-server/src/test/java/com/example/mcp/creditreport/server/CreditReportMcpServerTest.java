package com.example.mcp.creditreport.server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over the real protocol: starts {@link CreditReportMcpServer} as a subprocess with
 * {@code CREDIT_REPORTS_DIR} pointing at a temporary directory, and calls its tools with the
 * SDK's stdio client.
 */
class CreditReportMcpServerTest {

    @TempDir
    static Path dir;

    private static McpSyncClient client;
    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    @BeforeAll
    static void startServer() throws Exception {
        Files.writeString(dir.resolve("credit-report-2416-0737-69.md"), CreditReportStoreTest.REPORT);
        String java = ProcessHandle.current().info().command().orElse("java");
        ServerParameters params = ServerParameters.builder(java)
                .args("-cp", System.getProperty("java.class.path"), CreditReportMcpServer.class.getName())
                .addEnvVar("CREDIT_REPORTS_DIR", dir.toString())
                .build();
        client = McpClient.sync(new StdioClientTransport(params, JSON))
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void handshakeReportsServerInfoAndInstructions() {
        assertEquals("credit-report-mcp-server", client.getServerInfo().name());
        assertTrue(client.getServerInstructions().contains(dir.toAbsolutePath().normalize().toString()));
    }

    @Test
    void listsReadOnlyTools() {
        List<Tool> tools = client.listTools().tools();

        assertEquals(List.of("get_credit_report", "list_credit_reports"), tools.stream().map(Tool::name).sorted().toList());
        assertTrue(tools.stream().allMatch(t -> t.annotations().readOnlyHint()));
    }

    @Test
    void listCreditReportsReturnsJsonSummaries() throws Exception {
        CallToolResult result = client.callTool(new CallToolRequest("list_credit_reports", Map.of()));

        assertFalse(result.isError());
        List<Map<String, Object>> reports = JSON.readValue(text(result), new TypeRef<>() {
        });
        assertEquals(1, reports.size());
        assertEquals("2416-0737-69", reports.getFirst().get("reportNumber"));
        assertEquals("ELENA C MOCKRIDGE", reports.getFirst().get("preparedFor"));
    }

    @Test
    void getCreditReportReturnsMarkdown() {
        CallToolResult result = client.callTool(new CallToolRequest("get_credit_report", Map.of("reportNumber", "2416-0737-69")));

        assertFalse(result.isError());
        assertEquals(CreditReportStoreTest.REPORT, text(result));
    }

    @Test
    void unknownReportIsToolError() {
        CallToolResult result = client.callTool(new CallToolRequest("get_credit_report", Map.of("reportNumber", "1111-2222-33")));

        assertTrue(result.isError());
        assertEquals("No credit report with number 1111-2222-33", text(result));
    }

    @Test
    void pathTraversalIsRejected() {
        CallToolResult result = client.callTool(new CallToolRequest("get_credit_report", Map.of("reportNumber", "../../etc/passwd")));

        assertTrue(result.isError());
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
