package org.omc.ui.logging;

import ch.qos.logback.core.PropertyDefinerBase;

import org.omc.util.PathUtils;

/** Resolves the log directory before Logback opens any files. */
public final class LogDirectoryDefiner extends PropertyDefinerBase {

    /**
     * Uses OMC_LOG_DIR (or legacy LOG_DIR) as an exact directory override,
     * otherwise the application's XDG state directory. System properties take
     * precedence over environment variables for each override.
     *
     * @return the absolute directory containing the application's log files
     */
    @Override
    public String getPropertyValue() {
        return PathUtils.applicationLogDirectory().toString();
    }
}
