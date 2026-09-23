package org.omc.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.lang.reflect.Field;

import org.gnome.glib.GLib;
import org.gnome.glib.SourceFunc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Regression tests: raw GLib idle callbacks must not be scheduled (or run)
 * once shutdown begins, so a stray post-shutdown idle cannot touch a closing
 * window. {@code admitFiles} already guarded both at submission and inside
 * the callback; these tests pin the same guard for {@code updateFileList},
 * {@code showStatus}, {@code showProgressView} and {@code hideProgressView}.
 *
 * <p>
 * The window is a {@code CALLS_REAL_METHODS} mock (the real constructor needs
 * GTK) with only the {@code shutdownInProgress} flag set; the guarded paths
 * must return before touching any other field.
 * </p>
 */
class MainWindowShutdownGuardTest {

    private MainWindowJavaGi window;

    @BeforeEach
    void setUp() throws Exception {
        window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
        Field shutdown = MainWindowJavaGi.class.getDeclaredField("shutdownInProgress");
        shutdown.setAccessible(true);
        shutdown.setBoolean(window, true);
    }

    private void assertNoIdleScheduled(Runnable call) {
        try (MockedStatic<GLib> glib = mockStatic(GLib.class)) {
            call.run();
            glib.verify(() -> GLib.idleAdd(anyInt(), any(SourceFunc.class)), never());
        }
    }

    @Test
    void updateFileListAfterShutdownSchedulesNoIdle() {
        assertNoIdleScheduled(() -> window.updateFileList());
    }

    @Test
    void showStatusAfterShutdownSchedulesNoIdle() {
        assertNoIdleScheduled(() -> window.showStatus("message"));
    }

    @Test
    void showProgressViewAfterShutdownSchedulesNoIdle() {
        assertNoIdleScheduled(() -> window.showProgressView());
    }

    @Test
    void hideProgressViewAfterShutdownSchedulesNoIdle() {
        assertNoIdleScheduled(() -> window.hideProgressView());
    }
}
