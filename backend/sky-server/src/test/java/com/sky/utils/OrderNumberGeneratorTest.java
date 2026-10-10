package com.sky.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNumberGeneratorTest {

    @Test
    void generatesUniquePaymentCompatibleNumbersUnderBatchLoad() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String number = OrderNumberGenerator.next();
            assertEquals(32, number.length());
            numbers.add(number);
        }
        assertEquals(20_000, numbers.size());
        assertTrue(numbers.stream().allMatch(number -> number.matches("[0-9a-f]{32}")));
    }
}
