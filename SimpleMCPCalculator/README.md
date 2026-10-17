# SimpleMCPCalculator

A minimal [MCP](https://modelcontextprotocol.io) server and client, both Spring Boot apps built
on [Spring AI's MCP starters](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html).
The server exposes a calculator as MCP tools; the client launches the server and talks to it over
**stdio** (JSON-RPC on the server's stdin/stdout).

```
calculator-mcp-client ──(MCP over stdin/stdout)──> calculator-mcp-server
  (spring-ai-starter-mcp-client)                     (spring-ai-starter-mcp-server, @McpTool methods)
```

| Module                   | What it is                                                                  |
|--------------------------|-----------------------------------------------------------------------------|
| `calculator-mcp-server/` | Spring Boot MCP server; `CalculatorTools` holds the five `@McpTool` methods |
| `calculator-mcp-client/` | Spring Boot MCP client; starts the server jar and calls its tools from the command line |

Versions: Java 25+, Spring Boot 4.1, Spring AI 2.0.1 (MCP Java SDK 2.0).

## Tools

| Tool        | Arguments          | Returns                                       |
|-------------|--------------------|-----------------------------------------------|
| `add`       | `a`, `b` (numbers) | `a + b`                                       |
| `subtract`  | `a`, `b`           | `a - b`                                       |
| `multiply`  | `a`, `b`           | `a * b`                                       |
| `divide`    | `a`, `b`           | `a / b`; error result if `b` is 0             |
| `remainder` | `a`, `b`           | `a % b`; error result if `b` is 0             |

## Prerequisites

- **Java 25 or newer** (`java -version`)
- Nothing else. The Maven wrapper (`./mvnw`) downloads Maven on its first run.

All commands below are run from this `SimpleMCPCalculator/` directory.

## 1. Build

```bash
./mvnw package
```

This builds both executable jars (and runs the tests):

- `calculator-mcp-server/target/calculator-mcp-server.jar`
- `calculator-mcp-client/target/calculator-mcp-client.jar`

## 2. Start the server

With stdio there is no port to listen on: the server is started by whoever connects to it, and it
reads MCP requests from stdin and writes responses to stdout. **You don't start it yourself when
using the client** — the client launches it as a subprocess (see step 3).

To start it on its own, for example to try it by hand:

```bash
java -jar calculator-mcp-server/target/calculator-mcp-server.jar
```

It then waits for JSON-RPC messages on stdin. Paste these lines one at a time (the server takes a
few seconds to start):

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"manual","version":"1"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"add","arguments":{"a":7,"b":5}}}
```

Press `Ctrl+D` to stop it. Because stdout is the protocol channel, the server's logs go to a file
instead: `${java.io.tmpdir}/calculator-mcp-server.log` (usually `/tmp/calculator-mcp-server.log`).

## 3. Use the client

The client reads the server command from `spring.ai.mcp.client.stdio.connections.calculator` in
`calculator-mcp-client/src/main/resources/application.yml`, starts the server, performs the MCP
handshake, and calls the tools.

```bash
# Demo: list the server's tools and call each one
java -jar calculator-mcp-client/target/calculator-mcp-client.jar

# Call one tool: <operation> <a> <b>
java -jar calculator-mcp-client/target/calculator-mcp-client.jar add 7 5          # 12.0
java -jar calculator-mcp-client/target/calculator-mcp-client.jar subtract 7 5     # 2.0
java -jar calculator-mcp-client/target/calculator-mcp-client.jar multiply 6 7     # 42.0
java -jar calculator-mcp-client/target/calculator-mcp-client.jar divide 10 4      # 2.5
java -jar calculator-mcp-client/target/calculator-mcp-client.jar remainder 10 3   # 1.0
java -jar calculator-mcp-client/target/calculator-mcp-client.jar divide 1 0       # Error: Division by zero (exit code 1)
```

Demo output:

```
Tools offered by the server: add, divide, multiply, remainder, subtract
add       17, 5 = 22.0
subtract  17, 5 = 12.0
multiply  17, 5 = 85.0
divide    17, 5 = 3.4
remainder 17, 5 = 2.0
divide    1, 0  = Error: Division by zero
```

The client finds the server jar at `calculator-mcp-server/target/calculator-mcp-server.jar`,
relative to the current directory. To run it from somewhere else, pass the path:

```bash
java -jar /path/to/calculator-mcp-client.jar --calculator.server.jar=/path/to/calculator-mcp-server.jar add 7 5
```

## Use the server from Claude Code or Claude Desktop

Any MCP host can launch the server the same way the client does. For Claude Code:

```bash
claude mcp add calculator -- java -jar "$(pwd)/calculator-mcp-server/target/calculator-mcp-server.jar"
```

For Claude Desktop, add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "calculator": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/calculator-mcp-server.jar"]
    }
  }
}
```

## Tests

```bash
./mvnw verify
```

- `CalculatorToolsTest` unit-tests the arithmetic and the division-by-zero checks.
- `CalculatorClientEndToEndTest` launches the real server jar over stdio and calls every tool
  through `CalculatorClient`. The server module builds first, so its jar exists by the time
  this test runs. When the client module is built on its own and the jar is missing, the test
  is skipped.

## How it works

- **Server**: `spring-ai-starter-mcp-server` with `spring.ai.mcp.server.stdio=true`. Its annotation
  scanner registers every `@McpTool` method on a Spring bean and builds the input schema from
  the `@McpToolParam` parameters. An exception thrown by a tool becomes a result with
  `isError: true`. The banner, console logging and web server are switched off so nothing but
  protocol messages reaches stdout.
- **Client**: `spring-ai-starter-mcp-client` creates one initialized `McpSyncClient` for each
  configured stdio connection. `CalculatorCli` (an `ApplicationRunner`) wraps it in
  `CalculatorClient`, which sends `tools/call` requests, and then exits, which closes the
  connection and stops the server process.
