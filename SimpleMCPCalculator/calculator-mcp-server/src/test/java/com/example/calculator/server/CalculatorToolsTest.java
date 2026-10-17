package com.example.calculator.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculatorToolsTest {

    private final CalculatorTools tools = new CalculatorTools();

    @Test
    void basicArithmetic() {
        assertEquals(12, tools.add(7, 5));
        assertEquals(2, tools.subtract(7, 5));
        assertEquals(35, tools.multiply(7, 5));
        assertEquals(1.4, tools.divide(7, 5));
        assertEquals(2, tools.remainder(7, 5));
    }

    @Test
    void negativeAndFractionalOperands() {
        assertEquals(-2.5, tools.add(-5, 2.5));
        assertEquals(-1, tools.remainder(-7, 3));
        assertEquals(1.5, tools.remainder(7.5, 2));
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> tools.divide(1, 0));
        assertThrows(IllegalArgumentException.class, () -> tools.remainder(1, 0));
    }
}
