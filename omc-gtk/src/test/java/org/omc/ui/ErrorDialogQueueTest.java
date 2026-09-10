package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ErrorDialogQueue}, the pure queue that caps the number
 * of concurrent error windows. Excess dialogs wait in FIFO order and are shown
 * as earlier ones close. Pure Java (no GTK) so it runs headless.
 */
class ErrorDialogQueueTest {

    private final List<String> shown = new ArrayList<>();

    private Runnable shower(String id) {
        return () -> shown.add(id);
    }

    @Test
    void showsUpToCapImmediately() {
        ErrorDialogQueue queue = new ErrorDialogQueue(3);

        queue.offer(shower("e1"));
        queue.offer(shower("e2"));
        queue.offer(shower("e3"));

        assertEquals(List.of("e1", "e2", "e3"), shown);
        assertEquals(0, queue.queuedCount());
    }

    @Test
    void queuesBeyondCap() {
        ErrorDialogQueue queue = new ErrorDialogQueue(3);

        for (int i = 1; i <= 5; i++) {
            queue.offer(shower("e" + i));
        }

        assertEquals(List.of("e1", "e2", "e3"), shown, "Only the first cap dialogs may show");
        assertEquals(2, queue.queuedCount());
    }

    @Test
    void closingDialogShowsNextQueuedInFifoOrder() {
        ErrorDialogQueue queue = new ErrorDialogQueue(2);

        for (int i = 1; i <= 4; i++) {
            queue.offer(shower("e" + i));
        }
        assertEquals(List.of("e1", "e2"), shown);

        queue.onDialogClosed();
        assertEquals(List.of("e1", "e2", "e3"), shown, "Next queued dialog must show in FIFO order");
        assertEquals(1, queue.queuedCount());

        queue.onDialogClosed();
        assertEquals(List.of("e1", "e2", "e3", "e4"), shown);
        assertEquals(0, queue.queuedCount());
    }

    @Test
    void allQueuedDialogsEventuallyShown() {
        ErrorDialogQueue queue = new ErrorDialogQueue(3);

        for (int i = 1; i <= 10; i++) {
            queue.offer(shower("e" + i));
        }

        // Close all shown dialogs one by one; everything queued must appear.
        for (int i = 0; i < 7; i++) {
            queue.onDialogClosed();
        }

        assertEquals(10, shown.size());
        for (int i = 1; i <= 10; i++) {
            assertTrue(shown.contains("e" + i), "Dialog e" + i + " must eventually be shown");
        }
    }

    @Test
    void excessClosedEventsDoNotOvershow() {
        ErrorDialogQueue queue = new ErrorDialogQueue(2);

        queue.offer(shower("e1"));
        queue.offer(shower("e2"));
        queue.onDialogClosed();
        queue.onDialogClosed();
        queue.onDialogClosed(); // spurious close event

        assertEquals(List.of("e1", "e2"), shown, "No queued dialogs left to over-show");
    }

    @Test
    void rejectsNonPositiveCap() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorDialogQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new ErrorDialogQueue(-1));
    }

    @Test
    void throwingShowRunnableReleasesSlot() {
        ErrorDialogQueue queue = new ErrorDialogQueue(1);

        assertThrows(IllegalStateException.class, () -> queue.offer(() -> {
            throw new IllegalStateException("dialog construction failed");
        }));

        // The failed show must not leak its slot: the next dialog shows
        // immediately instead of queueing forever behind the failure.
        queue.offer(shower("next"));
        assertEquals(List.of("next"), shown);
        assertEquals(0, queue.queuedCount());
    }

    @Test
    void throwingQueuedRunnableReleasesSlotWhenShown() {
        ErrorDialogQueue queue = new ErrorDialogQueue(1);
        queue.offer(shower("e1"));
        queue.offer(() -> {
            throw new IllegalStateException("dialog construction failed");
        });
        assertEquals(1, queue.queuedCount());

        // Closing e1 transfers the slot to the queued runnable, which throws:
        // the transferred slot must be released, not leaked.
        assertThrows(IllegalStateException.class, queue::onDialogClosed);

        queue.offer(shower("after"));
        assertEquals(List.of("e1", "after"), shown);
        assertEquals(0, queue.queuedCount());
    }
}
