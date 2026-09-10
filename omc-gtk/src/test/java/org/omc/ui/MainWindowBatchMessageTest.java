package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Headless tests for the batch completion message composition of
 * {@link MainWindowJavaGi}. The message is composed from engine-derived counts
 * (per-file results), and the composition logic is a pure static method so it
 * can be verified without a GTK display.
 */
class MainWindowBatchMessageTest {

    @Test
    void allCancelled_messageSaysAllCancelled() {
        assertEquals("All conversions cancelled",
                MainWindowJavaGi.composeBatchCompletionMessage(3, 0, 0, 3));
    }

    @Test
    void allSuccessful_messageAnnouncesSuccess() {
        assertEquals("All 3 files converted successfully!",
                MainWindowJavaGi.composeBatchCompletionMessage(3, 3, 0, 0));
    }

    @Test
    void allFailed_messageAnnouncesFailure() {
        assertEquals("All 2 files failed to convert.",
                MainWindowJavaGi.composeBatchCompletionMessage(2, 0, 2, 0));
    }

    @Test
    void mixedResults_messageListsCounts() {
        assertEquals("Conversion complete: 3 successful, 2 failed out of 5 files.",
                MainWindowJavaGi.composeBatchCompletionMessage(5, 3, 2, 0));
    }

    @Test
    void mixedResultsWithCancellations_messageListsAllCounts() {
        assertEquals("Conversion complete: 3 successful, 2 failed, 1 cancelled out of 6 files.",
                MainWindowJavaGi.composeBatchCompletionMessage(6, 3, 2, 1));
    }

    @Test
    void zeroSuccessWithCancellations_reportsFailureBranch() {
        // Matches historical branch logic: successful == 0 -> failure message.
        assertEquals("All 2 files failed to convert.",
                MainWindowJavaGi.composeBatchCompletionMessage(2, 0, 1, 1));
    }

    @Test
    void successPlusRemovedFile_doesNotClaimAllSuccessful() {
        // 1 success + 1 file removed mid-conversion: the batch completed but
        // not every file converted, so the all-successful wording would lie.
        assertEquals("2 files processed: 1 succeeded, 1 removed during conversion",
                MainWindowJavaGi.composeBatchCompletionMessage(2, 1, 0, 0));
    }

    @Test
    void twoOfTwoSuccessful_stillAnnouncesAllSuccessful() {
        assertEquals("All 2 files converted successfully!",
                MainWindowJavaGi.composeBatchCompletionMessage(2, 2, 0, 0));
    }

    @Test
    void allRemoved_reportsRemovedFilesAccurately() {
        assertEquals("1 files processed: 0 succeeded, 1 removed during conversion",
                MainWindowJavaGi.composeBatchCompletionMessage(1, 0, 0, 0));
    }

    @Test
    void removedMixedWithFailure_listsAllOutcomes() {
        assertEquals("3 files processed: 1 succeeded, 1 failed, 1 removed during conversion",
                MainWindowJavaGi.composeBatchCompletionMessage(3, 1, 1, 0));
    }
}
