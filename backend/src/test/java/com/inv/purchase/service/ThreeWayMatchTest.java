package com.inv.purchase.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreeWayMatchTest {
    @Test
    void amountAndQtyMustBothAgree() {
        assertEquals("UNMATCHED", ThreeWayMatch.judge(new BigDecimal("100"), null, BigDecimal.ONE, BigDecimal.ONE));
        assertEquals("UNMATCHED", ThreeWayMatch.judge(new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ONE, null));
        assertEquals("MATCHED", ThreeWayMatch.judge(
                new BigDecimal("3390.00"), new BigDecimal("3390.005"), BigDecimal.ONE, new BigDecimal("1.0")));
        assertEquals("MISMATCH", ThreeWayMatch.judge(
                new BigDecimal("3390"), new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE));
        assertEquals("MISMATCH", ThreeWayMatch.judge(
                new BigDecimal("3390"), new BigDecimal("3390"), new BigDecimal("2"), BigDecimal.ONE));
    }
}
