# mcptest

A Spring Boot REST API for **items** and **persons**, wrapped by an
[MCP](https://modelcontextprotocol.io) server, with a Java MCP client that reads and writes the
API's data through MCP.

```
mcp-client ──(MCP over stdin/stdout)──> mcp-server ──(HTTP)──> REST API (localhost:8080/api/v1)
```

| Directory      | What it is                                                                 |
|----------------|----------------------------------------------------------------------------|
| `/` (root)     | REST API server (Spring Boot, spec-first from `src/main/resources/openapi/api.yaml`) |
| `mcp-server/`  | MCP server that exposes each REST operation as an MCP tool                 |
| `mcp-client/`  | MCP client that starts `mcp-server` and calls its tools                    |

## Prerequisites

- **Java 25 or newer** (`java -version`)
- Nothing else. The Maven wrapper (`./mvnw`) downloads Maven on its first run.

All commands below are run from the repository root.

## 1. Start the REST API server

```bash
./mvnw spring-boot:run
```

The API listens on port **8080**. Leave it running and use a second terminal for the next steps.
Check that it is up:

```bash
curl http://localhost:8080/actuator/health        # {"status":"UP"}
curl http://localhost:8080/api/v1/items           # []
```

To use a different port, run `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`
and set `API_BASE_URL` (see below) to match.

Data is stored in memory, so it is lost when the server stops.

## 2. Build and start the MCP server

Build the server jar:

```bash
./mvnw -f mcp-server/pom.xml package               # creates mcp-server/target/mcp-server.jar
```

The MCP server uses the **stdio** transport: an MCP client starts it as a child process and
talks to it over stdin/stdout. **You normally don't start it yourself.** The client in step 3
and Claude Code both start it for you.

To start it by hand anyway (for example to check that it starts), run:

```bash
API_BASE_URL=http://localhost:8080/api/v1 java -jar mcp-server/target/mcp-server.jar
```

It then waits for MCP messages on stdin. Press `Ctrl+C` to stop it.

To try the tools interactively in a browser, use the MCP Inspector (requires Node.js):

```bash
npx @modelcontextprotocol/inspector java -jar mcp-server/target/mcp-server.jar
```

### Tools

| Tool            | REST call                     | Arguments                                         |
|-----------------|-------------------------------|---------------------------------------------------|
| `list_items`    | `GET /items`                  | `nameContains` (optional)                         |
| `get_item`      | `GET /items/{id}`             | `id`                                              |
| `create_item`   | `POST /items`                 | `name`, `description`, `price`                    |
| `update_item`   | `PUT /items/{id}`             | `id`, `name`, `description`, `price`              |
| `delete_item`   | `DELETE /items/{id}`          | `id`                                              |
| `list_persons`  | `GET /persons`                | none                                              |
| `get_person`    | `GET /persons/{id}`           | `id`                                              |
| `create_person` | `POST /persons`               | `name`, `email`, `address`, `phoneNumber`         |
| `update_person` | `PUT /persons/{id}`           | `id`, `name`, `email`, `address`, `phoneNumber`   |
| `delete_person` | `DELETE /persons/{id}`        | `id`                                              |

Tools return the API's JSON response. A REST error (such as a 404) or an unreachable API comes
back as an MCP tool error with the details.

### Use it from Claude Code

```bash
claude mcp add rest-api -- java -jar "$(pwd)/mcp-server/target/mcp-server.jar"
```

Start a new Claude Code session (or run `/mcp`) and ask, for example, *"list all items under $50"*.

## 3. Run the client

Build the client jar:

```bash
./mvnw -f mcp-client/pom.xml package               # creates mcp-client/target/mcp-client.jar
```

With the REST API running (step 1) and the server jar built (step 2), run:

```bash
# Demo: lists the tools, creates items and a person, then lists, filters, updates and deletes them
java -jar mcp-client/target/mcp-client.jar

# List the server's tools
java -jar mcp-client/target/mcp-client.jar tools

# Call one tool, with its arguments as a JSON object
java -jar mcp-client/target/mcp-client.jar create_item '{"name":"Laptop","price":1499}'
java -jar mcp-client/target/mcp-client.jar list_items '{"nameContains":"lap"}'
java -jar mcp-client/target/mcp-client.jar get_person '{"id":1}'
```

The client starts `mcp-server.jar` itself, so you don't need a separate terminal for it. If a
tool call fails, the client prints the error and exits with code 1.

### Configuration

| Environment variable | Default                            | Used by                                 |
|----------------------|------------------------------------|-----------------------------------------|
| `API_BASE_URL`       | `http://localhost:8080/api/v1`     | mcp-server (the client passes it along) |
| `MCP_SERVER_JAR`     | `mcp-server/target/mcp-server.jar` | mcp-client                              |

## Running the REST API tests

```bash
./mvnw test
```
