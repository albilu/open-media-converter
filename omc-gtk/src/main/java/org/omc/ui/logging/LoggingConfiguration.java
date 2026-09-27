package org.omc.ui.logging;

import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.omc.util.ThreadUtils;

/** Owns logging bridges, fatal-thread reporting and application log shutdown. */
public final class LoggingConfiguration {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean STOPPED = new AtomicBoolean();

    private LoggingConfiguration() {
    }

    /** Installs diagnostic routing once, before application startup. */
    public static void initialize() {
        if (INITIALIZED.compareAndSet(false, true)) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            // Let Logback apply the configured level, including later --debug
            // requests forwarded by Gio to the already running application.
            java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.ALL);
            Thread.setDefaultUncaughtExceptionHandler(ThreadUtils::logUncaughtException);
            Runtime.getRuntime().addShutdownHook(
                    new Thread(LoggingConfiguration::shutdown, "logging-shutdown"));
        }
    }

    /** Enables DEBUG for console and file logs, including conversion services. */
    public static void enableDebugLogging() {
        context().getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
    }

    /** Closes log files and waits for archive compression, once, on exit. */
    public static void shutdown() {
        if (STOPPED.compareAndSet(false, true)) {
            context().stop();
        }
    }

    private static LoggerContext context() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }
}
