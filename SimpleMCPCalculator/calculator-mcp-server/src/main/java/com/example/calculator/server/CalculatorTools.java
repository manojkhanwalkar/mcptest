package com.example.calculator.server;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The calculator capabilities exposed as MCP tools. An exception thrown here is returned to the
 * client as a tool result with {@code isError=true} rather than breaking the connection.
 */
@Component
public class CalculatorTools {

    @McpTool(name = "add", description = "Add two numbers: a + b")
    public double add(@McpToolParam(description = "First operand") double a,
                      @McpToolParam(description = "Second operand") double b) {
        return a + b;
    }

    @McpTool(name = "subtract", description = "Subtract the second number from the first: a - b")
    public double subtract(@McpToolParam(description = "Number to subtract from") double a,
                           @McpToolParam(description = "Number to subtract") double b) {
        return a - b;
    }

    @McpTool(name = "multiply", description = "Multiply two numbers: a * b")
    public double multiply(@McpToolParam(description = "First factor") double a,
                           @McpToolParam(description = "Second factor") double b) {
        return a * b;
    }

    @McpTool(name = "divide", description = "Divide the first number by the second: a / b. Fails if b is 0")
    public double divide(@McpToolParam(description = "Dividend") double a,
                         @McpToolParam(description = "Divisor (must not be 0)") double b) {
        requireNonZero(b);
        return a / b;
    }

    @McpTool(name = "remainder", description = "Remainder of dividing the first number by the second: a % b. Fails if b is 0")
    public double remainder(@McpToolParam(description = "Dividend") double a,
                            @McpToolParam(description = "Divisor (must not be 0)") double b) {
        requireNonZero(b);
        return a % b;
    }

    private static void requireNonZero(double divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("Division by zero");
        }
    }
}
