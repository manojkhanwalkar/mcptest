package com.example.mcp.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines one MCP tool per REST operation in the api-server and turns each tool call into an
 * HTTP request through {@link RestApiClient}.
 *
 * <p>Argument routing is uniform for every tool:
 * <ul>
 *   <li>{@code id} fills the {@code {id}} placeholder in the path;</li>
 *   <li>the remaining arguments become query parameters for GET, or the JSON body for POST/PUT.</li>
 * </ul>
 * Input schemas mirror the constraints in api.yaml so the model sees the same rules the API enforces.
 */
public class RestApiTools {

    /** A REST operation exposed as an MCP tool. */
    private record Endpoint(String name, String description, String method, String path,
                            String inputSchema, boolean readOnly, boolean destructive) {
    }

    // ---------------------------------------------------------------- input schemas

    private static final String ID_PROPERTY = """
            "id": {"type": "integer", "minimum": 1, "description": "Server-assigned id"}""";

    private static final String ITEM_PROPERTIES = """
            "name": {"type": "string", "minLength": 1, "maxLength": 100},
            "description": {"type": "string", "maxLength": 500},
            "price": {"type": "number", "minimum": 0}""";

    private static final String PERSON_PROPERTIES = """
            "name": {"type": "string", "minLength": 1, "maxLength": 100},
            "address": {"type": "string", "maxLength": 300},
            "email": {"type": "string", "format": "email", "maxLength": 254},
            "phoneNumber": {"type": "string", "pattern": "^\\\\+?[0-9 ().-]{7,20}$",
                            "description": "Digits with optional leading +, spaces, dashes, dots or parentheses"}""";

    private static String schema(String properties, String... required) {
        String req = String.join(", ", java.util.Arrays.stream(required).map(r -> "\"" + r + "\"").toList());
        return "{\"type\": \"object\", \"properties\": {" + properties + "}, \"required\": [" + req + "]}";
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            // Items
            new Endpoint("list_items", "List all items, optionally filtered by a case-insensitive name substring.",
                    "GET", "/items",
                    schema("\"nameContains\": {\"type\": \"string\", \"description\": \"Case-insensitive filter on item name\"}"),
                    true, false),
            new Endpoint("get_item", "Get one item by id.", "GET", "/items/{id}",
                    schema(ID_PROPERTY, "id"), true, false),
            new Endpoint("create_item", "Create an item. Returns the created item with its id.", "POST", "/items",
                    schema(ITEM_PROPERTIES, "name"), false, false),
            new Endpoint("update_item", "Replace an item. Omitted optional fields are cleared.", "PUT", "/items/{id}",
                    schema(ID_PROPERTY + ",\n" + ITEM_PROPERTIES, "id", "name"), false, true),
            new Endpoint("delete_item", "Delete an item by id.", "DELETE", "/items/{id}",
                    schema(ID_PROPERTY, "id"), false, true),
            // Persons
            new Endpoint("list_persons", "List all persons.", "GET", "/persons",
                    schema(""), true, false),
            new Endpoint("get_person", "Get one person by id.", "GET", "/persons/{id}",
                    schema(ID_PROPERTY, "id"), true, false),
            new Endpoint("create_person", "Create a person. Returns the created person with its id.", "POST", "/persons",
                    schema(PERSON_PROPERTIES, "name", "email"), false, false),
            new Endpoint("update_person", "Replace a person. Omitted optional fields are cleared.", "PUT", "/persons/{id}",
                    schema(ID_PROPERTY + ",\n" + PERSON_PROPERTIES, "id", "name", "email"), false, true),
            new Endpoint("delete_person", "Delete a person by id.", "DELETE", "/persons/{id}",
                    schema(ID_PROPERTY, "id"), false, true));

    private final RestApiClient api;
    private final McpJsonMapper json;

    public RestApiTools(RestApiClient api, McpJsonMapper json) {
        this.api = api;
        this.json = json;
    }

    /** Tool specifications (definition + handler) for every REST operation. */
    public List<SyncToolSpecification> specifications() {
        return ENDPOINTS.stream()
                .map(endpoint -> SyncToolSpecification.builder()
                        .tool(toTool(endpoint))
                        .callHandler((exchange, request) -> call(endpoint, request.arguments()))
                        .build())
                .toList();
    }

    private Tool toTool(Endpoint endpoint) {
        return Tool.builder()
                .name(endpoint.name())
                .description(endpoint.description())
                .inputSchema(json, endpoint.inputSchema())
                .annotations(ToolAnnotations.builder()
                        .readOnlyHint(endpoint.readOnly())
                        .destructiveHint(endpoint.destructive())
                        .idempotentHint(!endpoint.method().equals("POST"))
                        .openWorldHint(false)
                        .build())
                .build();
    }

    /** Executes one tool call against the REST API. HTTP and I/O failures become isError results. */
    CallToolResult call(Endpoint endpoint, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? new HashMap<>() : new HashMap<>(arguments);
        String path = endpoint.path();
        if (path.contains("{id}")) {
            Object id = args.remove("id");
            if (!(id instanceof Number number)) {
                return error("Argument 'id' is required and must be an integer");
            }
            path = path.replace("{id}", Long.toString(number.longValue()));
        }

        try {
            boolean hasBody = endpoint.method().equals("POST") || endpoint.method().equals("PUT");
            RestApiClient.ApiResponse response = api.send(
                    endpoint.method(), path,
                    hasBody ? null : args,
                    hasBody ? json.writeValueAsString(args) : null);

            if (!response.isSuccess()) {
                return error("HTTP " + response.status() + " from " + endpoint.method() + " " + path + ": " + response.body());
            }
            String body = response.body() == null || response.body().isBlank()
                    ? "{\"status\": " + response.status() + ", \"message\": \"" + endpoint.method() + " " + path + " succeeded\"}"
                    : response.body();
            return CallToolResult.builder().addTextContent(body).isError(false).build();
        } catch (IOException e) {
            return error("Could not reach REST API at " + api.baseUrl() + ": " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Interrupted while calling REST API");
        }
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
