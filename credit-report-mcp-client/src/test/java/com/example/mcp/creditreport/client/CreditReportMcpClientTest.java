package com.example.mcp.creditreport.client;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the client and {@link CreditReportClientApp} against {@link FakeCreditReportMcpServer},
 * launched over stdio as a subprocess exactly as the real server would be.
 */
class CreditReportMcpClientTest {

    private static CreditReportMcpClient client;

    @BeforeAll
    static void connect() {
        String java = ProcessHandle.current().info().command().orElse("java");
        client = new CreditReportMcpClient(ServerParameters.builder(java)
                .args("-cp", System.getProperty("java.class.path"), FakeCreditReportMcpServer.class.getName())
                .build());
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void handshakeExposesServerInfo() {
        assertEquals("fake-credit-report-server", client.serverInfo().name());
    }

    @Test
    void listReportsParsesSummaries() {
        List<CreditReportMcpClient.ReportSummary> reports = client.listReports();

        assertEquals(List.of("0001-0002-03", "2416-0737-69"),
                reports.stream().map(CreditReportMcpClient.ReportSummary::reportNumber).toList());
        assertNull(reports.getFirst().preparedFor());
        assertEquals("ELENA C MOCKRIDGE", reports.get(1).preparedFor());
        assertEquals(4321, reports.get(1).sizeBytes());
    }

    @Test
    void getReportReturnsText() {
        assertEquals("# Report 2416-0737-69", client.getReport("2416-0737-69"));
    }

    @Test
    void unknownReportThrowsWithServerMessage() {
        CreditReportMcpClient.ToolCallException e =
                assertThrows(CreditReportMcpClient.ToolCallException.class, () -> client.getReport("1111-2222-33"));

        assertEquals("get_credit_report failed: No credit report with number 1111-2222-33", e.getMessage());
    }

    @Test
    void appPrintsFirstReportByDefault() {
        assertEquals("# Report 0001-0002-03\n", stdoutOf());
    }

    @Test
    void appPrintsRequestedReport() {
        assertEquals("# Report 2416-0737-69\n", stdoutOf("2416-0737-69"));
    }

    @Test
    void appListsReports() {
        String out = stdoutOf("list");

        assertTrue(out.contains("2416-0737-69   ELENA C MOCKRIDGE"), out);
        assertTrue(out.endsWith("2 report(s)\n"), out);
    }

    /** Runs the app with {@code args} and returns what it printed to stdout. */
    private static String stdoutOf(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, CreditReportClientApp.run(client, args));
        } finally {
            System.setOut(original);
        }
        return out.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
    }
}
