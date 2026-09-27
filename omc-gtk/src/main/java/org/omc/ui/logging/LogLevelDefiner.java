package org.omc.ui.logging;

import java.util.Locale;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.PropertyDefinerBase;

/** Resolves the initial logging level consistently for every launcher. */
public final class LogLevelDefiner extends PropertyDefinerBase {

    /**
     * Honors omc.logging.level, then OMC_LOG_LEVEL, then OMC_DEBUG. Invalid
     * explicit levels fall back to INFO; --debug can raise the level at runtime.
     *
     * @return a Logback level name, defaulting to INFO
     */
    @Override
    public String getPropertyValue() {
        String configured = System.getProperty("omc.logging.level");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("OMC_LOG_LEVEL");
        }
        if (configured != null && !configured.isBlank()) {
            Level level = Level.toLevel(configured.trim().toUpperCase(Locale.ROOT), null);
            if (level == null) {
                addWarn("Invalid logging level; using INFO");
                return Level.INFO.toString();
            }
            return level.toString();
        }
        String debug = System.getenv("OMC_DEBUG");
        return "1".equals(debug) || "true".equalsIgnoreCase(debug)
                ? Level.DEBUG.toString() : Level.INFO.toString();
    }
}
