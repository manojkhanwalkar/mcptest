package com.example.mcp.client;

import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Command-line front end for {@link RestApiMcpClient}.
 *
 * <pre>
 *   java -jar mcp-client/target/mcp-client.jar                    # run the demo scenario
 *   java -jar mcp-client/target/mcp-client.jar tools              # list the server's tools
 *   java -jar mcp-client/target/mcp-client.jar &lt;tool&gt; ['{json}']   # call one tool
 * </pre>
 *
 * Tool results go to stdout and status lines to stderr, so a single tool call's output is plain
 * JSON. Environment: {@code MCP_SERVER_JAR} (default {@code mcp-server/target/mcp-server.jar}) and
 * {@code API_BASE_URL} (default {@code http://localhost:8080/api/v1}).
 */
public class ClientApp {

    static final String DEFAULT_SERVER_JAR = "mcp-server/target/mcp-server.jar";

    public static void main(String[] args) {
        String serverJar = System.getenv().getOrDefault("MCP_SERVER_JAR", defaultServerJar());
        String apiBaseUrl = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080/api/v1");

        int exitCode = 0;
        try (RestApiMcpClient client = new RestApiMcpClient(serverJar, apiBaseUrl)) {
            System.err.println("Connected to " + client.serverInfo().name() + " " + client.serverInfo().version());
            if (args.length == 0) {
                demo(client);
            } else if (args[0].equals("tools")) {
                printTools(client.listTools());
            } else {
                Map<String, Object> arguments = args.length > 1 ? client.parseArguments(args[1]) : Map.of();
                System.out.println(client.toJson(client.call(args[0], arguments)));
            }
        } catch (RestApiMcpClient.ToolCallException e) {
            System.err.println(e.getMessage());
            exitCode = 1;
        } catch (IOException e) {
            System.err.println("Tool arguments must be a JSON object, e.g. '{\"id\":1}': " + e.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /** Exercises every kind of call and consumes the returned data. */
    private static void demo(RestApiMcpClient client) {
        List<Tool> tools = client.listTools();
        System.out.println("\n== Tools (" + tools.size() + ")");
        printTools(tools);

        System.out.println("\n== Creating data through MCP");
        Map<String, Object> laptop = client.createItem(item("Laptop", "14-inch, 32 GB RAM", 1499.0));
        Map<String, Object> mouse = client.createItem(item("Wireless mouse", "Bluetooth", 29.99));
        client.createItem(item("Laptop stand", "Aluminium", 45.5));
        Map<String, Object> ada = client.createPerson(person("Ada Lovelace", "ada@example.com", "+44 20 7946 0000"));
        System.out.println("created item   " + client.toJson(laptop));
        System.out.println("created item   " + client.toJson(mouse));
        System.out.println("created person " + client.toJson(ada));

        System.out.println("\n== Consuming the data");
        List<Map<String, Object>> items = client.listItems(null);
        double total = items.stream()
                .map(i -> i.get("price"))
                .filter(Number.class::isInstance)
                .mapToDouble(p -> ((Number) p).doubleValue())
                .sum();
        System.out.printf("%d items, total catalogue value %.2f%n", items.size(), total);
        for (Map<String, Object> i : items) {
            System.out.printf("  #%-3s %-20s %10s%n", i.get("id"), i.get("name"), i.get("price"));
        }

        List<Map<String, Object>> laptops = client.listItems("laptop");
        System.out.println("items matching 'laptop': " + laptops.stream().map(i -> i.get("name")).toList());

        List<Map<String, Object>> persons = client.listPersons();
        System.out.println(persons.size() + " persons: " + persons.stream().map(p -> p.get("name") + " <" + p.get("email") + ">").toList());

        System.out.println("\n== Update and delete");
        long mouseId = ((Number) mouse.get("id")).longValue();
        Map<String, Object> update = item("Wireless mouse", "Bluetooth, on sale", 19.99);
        update.put("id", mouseId);
        System.out.println("updated " + client.toJson(client.call("update_item", update)));
        System.out.println("get     " + client.toJson(client.getItem(mouseId)));
        System.out.println("delete  " + client.toJson(client.call("delete_item", Map.of("id", mouseId))));

        System.out.println("\n== Errors come back as MCP tool errors");
        try {
            client.getItem(mouseId);
        } catch (RestApiMcpClient.ToolCallException e) {
            System.out.println(e.getMessage());
        }
        try {
            client.createPerson(person("No Email", null, null));
        } catch (RestApiMcpClient.ToolCallException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void printTools(List<Tool> tools) {
        for (Tool tool : tools) {
            System.out.printf("  %-14s %s%n", tool.name(), tool.description());
        }
    }

    private static Map<String, Object> item(String name, String description, double price) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", description);
        item.put("price", price);
        return item;
    }

    private static Map<String, Object> person(String name, String email, String phone) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", name);
        if (email != null) person.put("email", email);
        if (phone != null) person.put("phoneNumber", phone);
        return person;
    }

    /** Finds the server jar whether we run from the repo root or from mcp-client/. */
    private static String defaultServerJar() {
        return Stream.of(DEFAULT_SERVER_JAR, "../" + DEFAULT_SERVER_JAR)
                .filter(p -> Files.exists(Path.of(p)))
                .findFirst()
                .orElse(DEFAULT_SERVER_JAR);
    }
}
