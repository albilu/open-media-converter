package org.omc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionStatus enum.
 * Requirement REQ-004.2, REQ-004.3: Status tracking through conversion
 * lifecycle.
 */
class ConversionStatusTest {

    @Test
    void testEnumValues() {
        // The complete status set is a contract: conversion lifecycle,
        // session restore and UI rendering all depend on it
        ConversionStatus[] values = ConversionStatus.values();
        assertEquals(5, values.length, "Should have exactly 5 status values");

        assertNotNull(ConversionStatus.PENDING);
        assertNotNull(ConversionStatus.IN_PROGRESS);
        assertNotNull(ConversionStatus.COMPLETED);
        assertNotNull(ConversionStatus.FAILED);
        assertNotNull(ConversionStatus.CANCELLED);
    }
}
