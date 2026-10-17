package com.example.calculator.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot MCP client. Spring AI's MCP client starter launches the server configured under
 * {@code spring.ai.mcp.client.stdio.connections} (see application.yml) and provides a connected,
 * initialized {@code McpSyncClient}, which {@link CalculatorCli} uses.
 */
@SpringBootApplication
public class CalculatorMcpClientApplication {

    public static void main(String[] args) {
        // Exit once the runner is done so the stdio connection (and the server process) is closed.
        System.exit(SpringApplication.exit(SpringApplication.run(CalculatorMcpClientApplication.class, args)));
    }
}
