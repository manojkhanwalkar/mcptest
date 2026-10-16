package com.example.mcp.creditreport.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command-line client that fetches a credit report through {@link CreditReportMcpClient} and
 * prints it to the console.
 *
 * <pre>
 *   java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar                 # print the first report
 *   java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar &lt;reportNumber&gt;  # print one report
 *   java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar list            # list the reports
 * </pre>
 *
 * The report goes to stdout and status lines go to stderr, so the report can be redirected to a
 * file. Environment: {@code CREDIT_REPORT_MCP_SERVER_JAR} (default
 * {@code credit-report-mcp-server/target/credit-report-mcp-server.jar}) and
 * {@code CREDIT_REPORTS_DIR} (default: the server's default, {@code test-data/credit-reports}).
 */
public class CreditReportClientApp {

    static final String DEFAULT_SERVER_JAR = "credit-report-mcp-server/target/credit-report-mcp-server.jar";

    public static void main(String[] args) {
        String serverJar = System.getenv().getOrDefault("CREDIT_REPORT_MCP_SERVER_JAR", defaultServerJar());
        String reportsDir = System.getenv("CREDIT_REPORTS_DIR");

        int exitCode = 0;
        try (CreditReportMcpClient client = new CreditReportMcpClient(serverJar, reportsDir)) {
            System.err.println("Connected to " + client.serverInfo().name() + " " + client.serverInfo().version());
            exitCode = run(client, args);
        } catch (CreditReportMcpClient.ToolCallException e) {
            System.err.println(e.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /** Runs one command and returns the process exit code. */
    static int run(CreditReportMcpClient client, String[] args) {
        if (args.length > 0 && args[0].equals("list")) {
            printList(client.listReports());
            return 0;
        }

        String reportNumber;
        if (args.length > 0) {
            reportNumber = args[0];
        } else {
            List<CreditReportMcpClient.ReportSummary> reports = client.listReports();
            if (reports.isEmpty()) {
                System.err.println("The server has no credit reports.");
                return 1;
            }
            reportNumber = reports.getFirst().reportNumber();
            if (reports.size() > 1) {
                System.err.println(reports.size() + " reports available (run with 'list' to see them); printing " + reportNumber);
            }
        }

        String report = client.getReport(reportNumber);
        System.err.println("Credit report " + reportNumber + ":\n");
        System.out.print(report.endsWith("\n") ? report : report + System.lineSeparator());
        System.out.flush();
        return 0;
    }

    private static void printList(List<CreditReportMcpClient.ReportSummary> reports) {
        System.out.printf("%-14s %-28s %-12s %s%n", "REPORT NUMBER", "PREPARED FOR", "DATE", "FILE");
        for (CreditReportMcpClient.ReportSummary r : reports) {
            System.out.printf("%-14s %-28s %-12s %s%n", r.reportNumber(),
                    r.preparedFor() == null ? "-" : r.preparedFor(),
                    r.dateGenerated() == null ? "-" : r.dateGenerated(),
                    r.fileName());
        }
        System.out.println(reports.size() + " report(s)");
    }

    /** Finds the server jar whether we run from the repo root or from credit-report-mcp-client/. */
    private static String defaultServerJar() {
        return Stream.of(DEFAULT_SERVER_JAR, "../" + DEFAULT_SERVER_JAR)
                .filter(p -> Files.exists(Path.of(p)))
                .findFirst()
                .orElse(DEFAULT_SERVER_JAR);
    }
}
