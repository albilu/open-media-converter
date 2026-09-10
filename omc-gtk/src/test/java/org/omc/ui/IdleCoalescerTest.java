package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IdleCoalescer}.
 *
 * <p>
 * The coalescer deduplicates rapid same-key UI updates into a single
 * GLib idle callback while guaranteeing that forced (terminal) events are
 * never dropped. GTK is avoided entirely by injecting a fake scheduler that
 * captures the flush task; the test invokes it to simulate the main loop.
 * </p>
 */
class IdleCoalescerTest {

    private final List<Integer> ran = new ArrayList<>();

    /** Captures scheduled flush tasks so tests can run the "main loop". */
    private static final class CapturingScheduler implements IdleCoalescer.IdleScheduler {
        final List<Runnable> scheduled = new ArrayList<>();
        int scheduleCalls;

        @Override
        public boolean schedule(Runnable flush) {
            scheduleCalls++;
            scheduled.add(flush);
            return true;
        }

        void runMainLoop() {
            List<Runnable> toRun = new ArrayList<>(scheduled);
            scheduled.clear();
            toRun.forEach(Runnable::run);
        }
    }

    @Test
    void hundredRapidUpdatesOnSameKey_scheduleSingleIdleAndRunOnlyLatest() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        for (int i = 0; i < 100; i++) {
            final int value = i;
            coalescer.submit("file-1", () -> ran.add(value));
        }

        assertEquals(1, scheduler.scheduleCalls, "100 rapid updates must schedule exactly one idle");
        assertEquals(1, scheduler.scheduled.size());

        scheduler.runMainLoop();

        assertEquals(1, ran.size(), "Only the latest update for the key must run");
        assertEquals(99, ran.get(0));
        assertFalse(coalescer.hasPendingWork(), "Flush must clear pending work");
    }

    @Test
    void distinctKeys_eachRunOnceInKeyArrivalOrder() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        coalescer.submit("a", () -> ran.add(1));
        coalescer.submit("b", () -> ran.add(2));
        coalescer.submit("a", () -> ran.add(3)); // replaces a's first task

        assertEquals(1, scheduler.scheduleCalls, "Pending work is batched into one scheduled idle");

        scheduler.runMainLoop();

        // Key "a" keeps its first-seen position but runs its LATEST task (3);
        // key "b" runs its only task (2). a's superseded task (1) is dropped.
        assertEquals(List.of(3, 2), ran, "Latest per key runs, keys keep arrival order");
    }

    @Test
    void forcedEvents_areNeverCoalescedAway() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        for (int i = 0; i < 5; i++) {
            final int value = i;
            coalescer.submitForced("terminal", () -> ran.add(value));
        }

        scheduler.runMainLoop();

        assertEquals(5, ran.size(), "Every forced event must run");
        assertEquals(List.of(0, 1, 2, 3, 4), ran, "Forced events keep submission order");
    }

    @Test
    void forcedEvent_schedulesFlushWhenIdleNotPending() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        assertEquals(0, scheduler.scheduleCalls);
        coalescer.submitForced("terminal", () -> ran.add(1));

        assertEquals(1, scheduler.scheduleCalls, "Forced events must not wait for a coalesced flush");
        assertTrue(coalescer.hasPendingWork());

        scheduler.runMainLoop();
        assertEquals(List.of(1), ran);
    }

    @Test
    void forcedEvent_flushesPendingCoalescedWorkWithIt() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        coalescer.submit("file-1", () -> ran.add(10)); // superseded
        coalescer.submit("file-1", () -> ran.add(11)); // latest, must run
        coalescer.submitForced("file-1", () -> ran.add(99)); // completion

        scheduler.runMainLoop();

        assertEquals(List.of(11, 99), ran, "Latest progress runs before the terminal event");
    }

    @Test
    void afterFlush_nextSubmitSchedulesNewIdle() {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);

        coalescer.submit("a", () -> ran.add(1));
        scheduler.runMainLoop();

        coalescer.submit("b", () -> ran.add(2));

        assertEquals(2, scheduler.scheduleCalls, "A new idle must be scheduled after a flush");
        scheduler.runMainLoop();
        assertEquals(List.of(1, 2), ran);
    }

    @Test
    void failedSchedule_runsTaskInlineAsFallback() {
        IdleCoalescer coalescer = new IdleCoalescer(task -> {
            task.run();
            return false;
        });

        coalescer.submit("a", () -> ran.add(1));
        coalescer.submitForced("b", () -> ran.add(2));

        assertEquals(List.of(1, 2), ran, "Tasks must still run when the scheduler refuses");
        assertFalse(coalescer.hasPendingWork());
    }

    @Test
    void concurrentSubmissions_neverLoseForcedEvents() throws InterruptedException {
        CapturingScheduler scheduler = new CapturingScheduler();
        IdleCoalescer coalescer = new IdleCoalescer(scheduler);
        // Simulate a main-loop thread that drains scheduled flushes once
        // both producers are done.
        CountDownLatch producersDone = new CountDownLatch(1);
        Thread drainer = new Thread(() -> {
            try {
                producersDone.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler.scheduled.forEach(Runnable::run);
        });
        drainer.start();

        AtomicInteger forcedRuns = new AtomicInteger();
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                coalescer.submit("k1", () -> { });
            }
            for (int i = 0; i < 50; i++) {
                coalescer.submitForced("k1-" + i, forcedRuns::incrementAndGet);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                coalescer.submit("k2", () -> { });
            }
            for (int i = 0; i < 50; i++) {
                coalescer.submitForced("k2-" + i, forcedRuns::incrementAndGet);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        producersDone.countDown();
        drainer.join();

        assertEquals(100, forcedRuns.get(), "Every forced event from every thread must run exactly once");
        assertTrue(scheduler.scheduleCalls >= 1);
    }
}
