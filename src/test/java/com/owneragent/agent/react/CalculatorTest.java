package com.owneragent.agent.react;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "'(12+8)*3', 60",
            "'10/4', 2.5",
            "'-3 + 5 * 2', 7",
            "'7 % 4', 3",
            "' 1.5 + 2.25 ', 3.75",
            "'--2', 2",
            "'2 * (3 + 4) - 5', 9"
    })
    void evaluatesExpression(String expression, double expected) {
        assertEquals(expected, Calculator.evaluate(expression), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "1 + a",
            "2 / 0",
            "2 % 0",
            "(1+2",
            "1.2.3",
            ".",
            "1 +"
    })
    void rejectsInvalidExpression(String expression) {
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate(expression));
    }

    @Test
    void rejectsNullExpression() {
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate(null));
    }

    @Test
    void formatsWholeNumbersWithoutDecimalPart() {
        assertEquals("60", Calculator.format(60.0));
        assertEquals("-7", Calculator.format(-7.0));
        assertEquals("0", Calculator.format(0.0));
        assertEquals("2.5", Calculator.format(2.5));
    }
}
