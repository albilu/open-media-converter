package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Headless tests for the status message produced after a background file
 * admission run. Every terminal outcome (success or failure) must replace
 * the transient "Reading files…" status so the bar is never left stuck.
 */
class MainWindowAdmissionStatusTest {

    @Test
    void successfulAdmission_reportsAddedCount() {
        assertEquals("3 file(s) added", MainWindowJavaGi.admissionStatusMessage(3, true));
    }

    @Test
    void failedAdmission_reportsFailureNotReadingStatus() {
        assertEquals("Failed to add files", MainWindowJavaGi.admissionStatusMessage(0, false));
    }

    @Test
    void zeroFilesAddedOnSuccess_stillReportsCount() {
        assertEquals("0 file(s) added", MainWindowJavaGi.admissionStatusMessage(0, true));
    }
}
