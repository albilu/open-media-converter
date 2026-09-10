package org.omc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThreadUtils} thread factory naming. Factory-created threads
 * must always carry distinct names, including when multiple threads create
 * them concurrently (duplicate names break log forensics and JMX diagnostics).
 */
class ThreadUtilsTest {

    @Test
    void threadFactory_sequentialCreation_producesIndexedNames() {
        ThreadFactory factory = ThreadUtils.createThreadFactory("Worker");

        Thread t0 = factory.newThread(() -> {
        });
        Thread t1 = factory.newThread(() -> {
        });
        Thread t2 = factory.newThread(() -> {
        });

        assertEquals("Worker-0", t0.getName());
        assertEquals("Worker-1", t1.getName());
        assertEquals("Worker-2", t2.getName());
        assertTrue(t0.isDaemon(), "factory threads must be daemon");
    }

    @Test
    void threadFactory_concurrentCreation_producesUniqueNames() throws InterruptedException {
        int producerThreads = 24;
        int namesPerThread = 50;
        ThreadFactory factory = ThreadUtils.createThreadFactory("Racy");
        List<String> names = new CopyOnWriteArrayList<>();
        CountDownLatch startBarrier = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(producerThreads);

        ExecutorService executor = Executors.newFixedThreadPool(producerThreads);
        try {
            for (int t = 0; t < producerThreads; t++) {
                executor.submit(() -> {
                    try {
                        startBarrier.await();
                        for (int i = 0; i < namesPerThread; i++) {
                            names.add(factory.newThread(() -> {
                            }).getName());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            startBarrier.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "name producers must finish");
        } finally {
            executor.shutdownNow();
        }

        int expectedCount = producerThreads * namesPerThread;
        assertEquals(expectedCount, names.size(), "all names must be collected");
        assertEquals(expectedCount, Set.copyOf(names).size(),
                "every generated thread name must be unique");
    }
}
