# mcptest

[![Tests](https://github.com/manojkhanwalkar/mcptest/actions/workflows/tests.yml/badge.svg)](https://github.com/manojkhanwalkar/mcptest/actions/workflows/tests.yml)

A Spring Boot REST API for **items** and **persons**, wrapped by an
[MCP](https://modelcontextprotocol.io) server, with a Java MCP client that reads and writes the
API's data through MCP. A second MCP server and client pair serves the dummy credit reports
from the file system.

```
mcp-client ──(MCP over stdin/stdout)──> mcp-server ──(HTTP)──> REST API (localhost:8080/api/v1)

credit-report-mcp-client ──(MCP over stdin/stdout)──> credit-report-mcp-server ──(file I/O)──> test-data/credit-reports/*.md
```

| Directory      | What it is                                                                 |
|----------------|----------------------------------------------------------------------------|
| `/` (root)     | REST API server (Spring Boot, spec-first from `src/main/resources/openapi/api.yaml`) |
| `mcp-server/`  | MCP server that exposes each REST operation as an MCP tool                 |
| `mcp-client/`  | MCP client that starts `mcp-server` and calls its tools                    |
| `credit-report-mcp-server/` | MCP server that serves credit report files from a directory   |
| `credit-report-mcp-client/` | MCP client that starts `credit-report-mcp-server` and prints a report |
| `mcp-parent/`  | Shared Maven parent (dependencies, runnable-jar build) for the four MCP modules |
| `test-data/`   | Sample output of the dummy credit report generator (see below)             |

The MCP calculator server and client that used to live in `SimpleMCPCalculator/` are now a
separate project: [manojkhanwalkar/SimpleMCPCalculator](https://github.com/manojkhanwalkar/SimpleMCPCalculator).

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

The API has no authentication, so it listens on `127.0.0.1` only and other machines can't
reach it. To expose it, put it behind a proxy that authenticates callers and set
`SERVER_ADDRESS` (for example `SERVER_ADDRESS=0.0.0.0`). Request bodies over 64 KB are rejected
(`api.max-request-body-bytes` in `application.properties`).

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

The client starts `mcp-server.jar` itself, so you don't need a separate terminal for it. Results
go to stdout and status lines to stderr, so a single tool call's output is plain JSON (e.g. it can
be piped to `jq`). If a tool call fails or the arguments aren't a JSON object, the client prints
the error and exits with code 1.

### Configuration

| Environment variable | Default                            | Used by                                 |
|----------------------|------------------------------------|-----------------------------------------|
| `API_BASE_URL`       | `http://localhost:8080/api/v1`     | mcp-server (the client passes it along) |
| `MCP_SERVER_JAR`     | `mcp-server/target/mcp-server.jar` | mcp-client                              |

## Dummy credit report generator

`com.example.testdata.creditreport` (in the root module) generates fictitious consumer credit
reports for testing and saves them as Markdown. The layout follows the Experian consumer credit
report: at a glance, personal information, accounts with a 24-month payment history, public
records, hard and soft inquiries, personal statements, and contact information.

```bash
./mvnw -q compile
java -cp target/classes com.example.testdata.creditreport.CreditReportCreator [outputDir] [seed]
```

| Argument    | Default                    | Meaning                                            |
|-------------|----------------------------|----------------------------------------------------|
| `outputDir` | `test-data/credit-reports` | Directory to write to; created if it doesn't exist |
| `seed`      | random                     | Random seed; the program prints the seed it used   |

The report is written to `<outputDir>/credit-report-<reportNumber>.md`. The same seed gives the
same report when run on the same day (the report is dated today). For example, the sample
report in `test-data/credit-reports/` was made with:

```bash
java -cp target/classes com.example.testdata.creditreport.CreditReportCreator test-data/credit-reports 20261015
```

All data is fake. Creditor and employer names are invented, phone numbers use the reserved
555-01xx range, SSNs are masked with the never-issued 9xx prefix, and every report starts with
a "TEST DATA — FICTITIOUS" banner.

## Credit report MCP server and client

`credit-report-mcp-server` serves the credit reports in a directory (by default
`test-data/credit-reports/`) over MCP. `credit-report-mcp-client` starts it, fetches a report
through MCP and prints it to the console. Neither needs the REST API.

Build both jars:

```bash
./mvnw -f credit-report-mcp-server/pom.xml package   # creates credit-report-mcp-server/target/credit-report-mcp-server.jar
./mvnw -f credit-report-mcp-client/pom.xml package   # creates credit-report-mcp-client/target/credit-report-mcp-client.jar
```

Run the client:

```bash
# Print the first report (by report number)
java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar

# Print one report
java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar 2416-0737-69

# List the available reports
java -jar credit-report-mcp-client/target/credit-report-mcp-client.jar list
```

The report goes to stdout exactly as stored, and status lines go to stderr, so
`... 2416-0737-69 > report.md` saves just the report. An unknown report number prints the error
and exits with code 1.

### Tools

| Tool                  | Arguments      | Returns                                                                    |
|-----------------------|----------------|----------------------------------------------------------------------------|
| `list_credit_reports` | none           | JSON array: `reportNumber`, `preparedFor`, `dateGenerated`, `fileName`, `sizeBytes` |
| `get_credit_report`   | `reportNumber` | The report's Markdown text                                                 |

Only files named `credit-report-<reportNumber>.md` directly in the directory are served, and
`reportNumber` must look like `1234-5678-90`, so the tools can't read other files.

To use the server from Claude Code:

```bash
claude mcp add credit-reports --env CREDIT_REPORTS_DIR="$(pwd)/test-data/credit-reports" \
  -- java -jar "$(pwd)/credit-report-mcp-server/target/credit-report-mcp-server.jar"
```

### Configuration

| Environment variable           | Default                                                        | Used by                               |
|--------------------------------|----------------------------------------------------------------|---------------------------------------|
| `CREDIT_REPORTS_DIR`           | `test-data/credit-reports` (looked up in `.` then `..`)        | server (the client passes it along)   |
| `CREDIT_REPORT_MCP_SERVER_JAR` | `credit-report-mcp-server/target/credit-report-mcp-server.jar` | client                                |

## Running the tests

```bash
./mvnw test                                        # REST API and credit report generator
./mvnw -f mcp-server/pom.xml test                  # MCP server
./mvnw -f mcp-client/pom.xml test                  # MCP client
./mvnw -f credit-report-mcp-server/pom.xml test    # credit report MCP server
./mvnw -f credit-report-mcp-client/pom.xml test    # credit report MCP client
```

The MCP tests don't need the REST API running:

- **mcp-server** tests run against a fake REST API (the JDK's built-in HTTP server on a random
  port). `RestApiMcpServerTest` also starts the real MCP server as a subprocess and talks to it
  over stdio.
- **mcp-client** tests start a small fake MCP server (`FakeMcpServer`) over stdio, so they test
  the client without `mcp-server` or the REST API.
- **credit-report-mcp-server** tests use report files in a temporary directory, and also start
  the real server as a subprocess over stdio.
- **credit-report-mcp-client** tests use a fake MCP server (`FakeCreditReportMcpServer`) with
  canned reports.
