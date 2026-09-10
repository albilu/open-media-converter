package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Headless tests for {@link MainWindowJavaGi.BatchUiState}, the pure batch
 * tracking state extracted from the GTK window so counter semantics, the
 * re-entrancy guard and the completion check are unit-testable without a
 * display.
 *
 * <p>
 * Covers the batch-lifecycle defects: rejected/double start must not corrupt
 * a running batch, results for files removed mid-batch must still complete
 * the batch, and list button sensitivity must not be refreshed while a
 * batch runs.
 * </p>
 */
class MainWindowBatchUiStateTest {

    private MainWindowJavaGi.BatchUiState runningBatchOfTwo() {
        MainWindowJavaGi.BatchUiState state = MainWindowJavaGi.BatchUiState.idle();
        assertTrue(state.beginBatch(List.of("a", "b")), "idle state must accept a batch");
        state.markStarted();
        return state;
    }

    // ===== Fix 2: re-entrancy guard / counter corruption =====

    @Test
    void secondStartWhileBatchRunning_isRejectedAndLeavesCountersUntouched() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        state.recordSuccess();

        assertTrue(state.isRunning(), "batch with started flag must report running");
        assertFalse(state.beginBatch(List.of("c", "d", "e")),
                "a second start while a batch runs must be rejected");
        assertEquals(2, state.totalFiles(), "total must come from the running batch");
        assertEquals(1, state.completedFiles());
        assertEquals(1, state.successfulFiles());
        assertEquals(List.of("a", "b"), state.batchFileIds());
    }

    @Test
    void idleState_allowsStart() {
        MainWindowJavaGi.BatchUiState state = MainWindowJavaGi.BatchUiState.idle();
        assertFalse(state.isRunning());
        assertTrue(state.beginBatch(List.of("a")));
    }

    @Test
    void failedStart_neverMarkedRunning_rejectsNothingAndAllowsRetry() {
        // handleConvert showed progress UI, controller.handleStartConversion()
        // threw, so markStarted() never ran: state must remain idle so the UI
        // rollback (hide progress, restore buttons per list state) is allowed.
        MainWindowJavaGi.BatchUiState state = MainWindowJavaGi.BatchUiState.idle();
        assertTrue(state.beginBatch(List.of("a")));
        // no markStarted(): the start failed
        assertFalse(state.isRunning());
        assertTrue(state.beginBatch(List.of("a", "b")), "retry after a failed start must be allowed");
        assertTrue(state.shouldRefreshListButtonSensitivity(),
                "button sensitivity refresh must be allowed when no batch is running");
    }

    // ===== Fix 3: removed-file batch stall =====

    @Test
    void resultForFileRemovedMidBatch_countsAsCompletedOnly_soBatchCompletes() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        state.recordSuccess();
        assertFalse(state.isBatchComplete());

        state.recordRemovedFileResult();

        assertEquals(2, state.completedFiles(), "removed file must count as completed");
        assertEquals(1, state.successfulFiles(), "removed file must not count as success");
        assertEquals(0, state.failedFiles(), "removed file must not count as failure");
        assertEquals(0, state.cancelledFiles());
        assertTrue(state.isBatchComplete(), "batch must complete when all files are accounted for");
    }

    @Test
    void batchOfOnlyRemovedFiles_completesWithoutClassification() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        state.recordRemovedFileResult();
        state.recordRemovedFileResult();

        assertTrue(state.isBatchComplete());
        assertEquals(0, state.successfulFiles());
        assertEquals(0, state.failedFiles());
        assertEquals(0, state.cancelledFiles());
    }

    // ===== Completion latch semantics (used by onBatchComplete) =====

    @Test
    void markBatchCompleted_latchesUntilEndBatch() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        state.recordSuccess();
        state.recordSuccess();
        assertTrue(state.isBatchComplete());
        state.markBatchCompleted();
        assertFalse(state.isBatchComplete(), "completion must latch so onBatchComplete fires once");

        state.endBatch();
        assertFalse(state.isRunning());
        assertEquals(0, state.totalFiles());
        assertEquals(0, state.completedFiles());
        assertEquals(List.of(), state.batchFileIds());
        assertTrue(state.beginBatch(List.of("x")), "a fresh batch may begin after endBatch");
    }

    // ===== Fix 4: list button sensitivity gating =====

    @Test
    void whileRunning_listButtonSensitivityMustNotBeRefreshed() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        assertFalse(state.shouldRefreshListButtonSensitivity(),
                "updateFileList must leave Convert/Clear All alone during a batch");
    }

    @Test
    void afterEndBatch_listButtonSensitivityMayBeRefreshed() {
        MainWindowJavaGi.BatchUiState state = runningBatchOfTwo();
        state.endBatch();
        assertTrue(state.shouldRefreshListButtonSensitivity());
    }
}
