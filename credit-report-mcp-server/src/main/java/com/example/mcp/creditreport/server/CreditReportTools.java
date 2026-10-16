package com.example.mcp.creditreport.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The MCP tools for reading credit reports, backed by {@link CreditReportStore}.
 *
 * <ul>
 *   <li>{@code list_credit_reports}: JSON array of the reports in the directory</li>
 *   <li>{@code get_credit_report}: the full Markdown text of one report</li>
 * </ul>
 * Both tools are read-only. File system failures and unknown reports become isError results.
 */
public class CreditReportTools {

    private static final String LIST_SCHEMA = """
            {"type": "object", "properties": {}}""";

    // The pattern comes from the store so the schema and the server-side check can't drift apart.
    private static final String GET_SCHEMA = """
            {"type": "object",
             "properties": {
               "reportNumber": {"type": "string", "pattern": "^%s$",
                                "description": "Report number, e.g. 2416-0737-69 (see list_credit_reports)"}
             },
             "required": ["reportNumber"]}""".formatted(CreditReportStore.REPORT_NUMBER.pattern().replace("\\", "\\\\"));

    private final CreditReportStore store;
    private final McpJsonMapper json;

    public CreditReportTools(CreditReportStore store, McpJsonMapper json) {
        this.store = store;
        this.json = json;
    }

    /** Tool specifications (definition + handler) for every credit report tool. */
    public List<SyncToolSpecification> specifications() {
        return List.of(
                spec("list_credit_reports",
                        "List the credit reports available on the server: report number, who it was prepared for, date generated, file name and size.",
                        LIST_SCHEMA, args -> listReports()),
                spec("get_credit_report",
                        "Get the full text of one credit report, as Markdown, by its report number.",
                        GET_SCHEMA, this::getReport));
    }

    private SyncToolSpecification spec(String name, String description, String schema,
                                       Function<Map<String, Object>, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(json, schema)
                        .annotations(ToolAnnotations.builder()
                                .readOnlyHint(true)
                                .destructiveHint(false)
                                .idempotentHint(true)
                                .openWorldHint(false)
                                .build())
                        .build())
                .callHandler((exchange, request) -> handler.apply(request.arguments() == null ? Map.of() : request.arguments()))
                .build();
    }

    CallToolResult listReports() {
        try {
            return ok(json.writeValueAsString(store.list()));
        } catch (IOException e) {
            return error("Could not list credit reports in " + store.directory() + ": " + e);
        }
    }

    CallToolResult getReport(Map<String, Object> arguments) {
        Object reportNumber = arguments.get("reportNumber");
        if (!(reportNumber instanceof String number)) {
            return error("Argument 'reportNumber' is required and must be a string");
        }
        try {
            return ok(store.read(number));
        } catch (IllegalArgumentException | CreditReportStore.ReportNotFoundException e) {
            return error(e.getMessage());
        } catch (IOException e) {
            return error("Could not read credit report " + number + " from " + store.directory() + ": " + e);
        }
    }

    private static CallToolResult ok(String text) {
        return CallToolResult.builder().addTextContent(text).isError(false).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
