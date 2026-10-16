package com.example.mcp.server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over the real protocol: starts {@link RestApiMcpServer} as a subprocess, connects
 * with the SDK's stdio client and calls tools, with a {@link FakeRestApi} behind the server.
 */
class RestApiMcpServerTest {

    private static FakeRestApi fake;
    private static McpSyncClient client;

    @BeforeAll
    static void startServer() throws Exception {
        fake = new FakeRestApi();
        String java = ProcessHandle.current().info().command().orElse("java");
        ServerParameters params = ServerParameters.builder(java)
                .args("-cp", System.getProperty("java.class.path"), RestApiMcpServer.class.getName())
                .addEnvVar("API_BASE_URL", fake.baseUrl())
                .build();
        client = McpClient.sync(new StdioClientTransport(params, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.closeGracefully();
        }
        fake.close();
    }

    @Test
    void handshakeReportsServerInfoAndInstructions() {
        assertEquals("rest-api-mcp-server", client.getServerInfo().name());
        assertTrue(client.getServerInstructions().contains(fake.baseUrl()));
        assertTrue(client.getServerCapabilities().tools() != null);
    }

    @Test
    void listsAllTools() {
        assertEquals(10, client.listTools().tools().size());
    }

    @Test
    void toolCallReachesRestApi() {
        fake.respondWith(200, "{\"id\":3,\"name\":\"Laptop stand\"}");

        CallToolResult result = client.callTool(new CallToolRequest("get_item", Map.of("id", 3)));

        assertFalse(result.isError());
        assertEquals("{\"id\":3,\"name\":\"Laptop stand\"}", ((TextContent) result.content().getFirst()).text());
        assertEquals("/api/v1/items/3", fake.lastRequest().path());
    }

    @Test
    void inputViolatingSchemaIsRejectedBeforeHttpCall() {
        int before = fake.requests().size();

        // email is required by create_person's input schema
        CallToolResult result = client.callTool(new CallToolRequest("create_person", Map.of("name", "No Email")));

        assertTrue(result.isError());
        assertTrue(((TextContent) result.content().getFirst()).text().contains("email"));
        assertEquals(before, fake.requests().size());
    }
}
