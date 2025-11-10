package org.omc.util;

import java.util.concurrent.*;

/**
 * Utility class for thread pool creation and management.
 * Provides factory methods for creating properly configured thread pools.
 * 
 * Requirements: All
 */
public final class ThreadUtils {

    private static final int DEFAULT_CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_MAX_POOL_SIZE = DEFAULT_CORE_POOL_SIZE * 2;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    // Private constructor to prevent instantiation
    private ThreadUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a fixed thread pool for conversion operations.
     *
     * @param threadCount The number of threads
     * @return A fixed thread pool executor
     */
    public static ExecutorService createFixedThreadPool(int threadCount) {
        ValidationUtils.requirePositive(threadCount, "threadCount");

        return Executors.newFixedThreadPool(
                threadCount,
                createThreadFactory("Conversion-Worker"));
    }

    /**
     * Creates a cached thread pool that creates threads as needed.
     *
     * @return A cached thread pool executor
     */
    public static ExecutorService createCachedThreadPool() {
        return Executors.newCachedThreadPool(
                createThreadFactory("Cached-Worker"));
    }

    /**
     * Creates a bounded thread pool with configurable core and max sizes.
     *
     * @param corePoolSize  The core pool size
     * @param maxPoolSize   The maximum pool size
     * @param queueCapacity The capacity of the work queue
     * @return A bounded thread pool executor
     */
    public static ThreadPoolExecutor createBoundedThreadPool(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity) {

        ValidationUtils.requirePositive(corePoolSize, "corePoolSize");
        ValidationUtils.requirePositive(maxPoolSize, "maxPoolSize");
        ValidationUtils.requirePositive(queueCapacity, "queueCapacity");

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be >= corePoolSize");
        }

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                DEFAULT_KEEP_ALIVE_TIME,
                DEFAULT_TIME_UNIT,
                workQueue,
                createThreadFactory("Bounded-Worker"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Creates a default conversion thread pool.
     * Uses available processors as core size and 2x as max size.
     *
     * @return A thread pool executor suitable for conversion operations
     */
    public static ThreadPoolExecutor createDefaultConversionThreadPool() {
        return createBoundedThreadPool(
                DEFAULT_CORE_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE,
                100 // Queue capacity
        );
    }

    /**
     * Creates a single-threaded executor for sequential operations.
     *
     * @param threadName The name for the thread
     * @return A single-threaded executor
     */
    public static ExecutorService createSingleThreadExecutor(String threadName) {
        ValidationUtils.requireNotBlank(threadName, "threadName");

        return Executors.newSingleThreadExecutor(
                createThreadFactory(threadName));
    }

    /**
     * Creates a scheduled thread pool for periodic tasks.
     *
     * @param corePoolSize The core pool size
     * @return A scheduled thread pool executor
     */
    public static ScheduledExecutorService createScheduledThreadPool(int corePoolSize) {
        ValidationUtils.requirePositive(corePoolSize, "corePoolSize");

        return Executors.newScheduledThreadPool(
                corePoolSize,
                createThreadFactory("Scheduled-Worker"));
    }

    /**
     * Creates a thread factory with custom naming and daemon settings.
     *
     * @param threadNamePrefix The prefix for thread names
     * @return A thread factory
     */
    public static ThreadFactory createThreadFactory(String threadNamePrefix) {
        return new ThreadFactory() {
            private int counter = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(threadNamePrefix + "-" + counter++);
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, e) -> {
                    System.err.println("Uncaught exception in thread " + t.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                });
                return thread;
            }
        };
    }

    /**
     * Shuts down an executor service gracefully.
     * Waits for tasks to complete up to the specified timeout.
     *
     * @param executor       The executor service to shut down
     * @param timeoutSeconds The timeout in seconds
     * @return true if executor terminated cleanly, false if timeout occurred
     */
    public static boolean shutdownGracefully(ExecutorService executor, long timeoutSeconds) {
        if (executor == null || executor.isShutdown()) {
            return true;
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                return executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
            }
            return true;
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gets the number of available processors.
     *
     * @return The number of available processors
     */
    public static int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Sleeps for the specified duration without throwing checked exception.
     *
     * @param millis The duration in milliseconds
     * @return true if sleep completed normally, false if interrupted
     */
    public static boolean sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
