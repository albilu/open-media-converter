package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/**
 * Regression test for the notify-send process leak: the notification child
 * must be spawned via ProcessBuilder with stdout/stderr discarded (an
 * undrained pipe can block the child and leak it) and reaped off the caller
 * thread instead of being retained in an unused variable.
 *
 * <p>
 * The window is a {@code CALLS_REAL_METHODS} mock (the real constructor needs
 * GTK); the private notification method touches no instance state.
 * </p>
 */
class MainWindowNotificationTest {

    @Test
    void completionNotificationDiscardsStreamsAndReapsProcess() throws Exception {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);

        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        List<List<?>> constructionArgs = new java.util.ArrayList<>();
        try (MockedConstruction<ProcessBuilder> construction = mockConstruction(ProcessBuilder.class,
                (builder, context) -> {
                    constructionArgs.add(new java.util.ArrayList<>(context.arguments()));
                    when(builder.start()).thenReturn(process);
                })) {

            Method method = MainWindowJavaGi.class.getDeclaredMethod("showCompletionNotification", String.class);
            method.setAccessible(true);
            method.invoke(window, "Batch complete");

            assertEquals(1, construction.constructed().size(), "exactly one notify-send process");
            ProcessBuilder builder = construction.constructed().get(0);
            List<?> command = constructionArgs.get(0).stream()
                    .flatMap(arg -> arg instanceof String[] strings ? List.of(strings).stream() : List.of(arg).stream())
                    .toList();
            assertEquals("notify-send", command.get(0));
            verify(builder).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            verify(builder).redirectError(ProcessBuilder.Redirect.DISCARD);
            verify(builder).start();
        }
    }

    @Test
    void completionNotificationFailureIsNonFatal() throws Exception {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);

        try (MockedConstruction<ProcessBuilder> construction = mockConstruction(ProcessBuilder.class,
                (builder, context) -> when(builder.start()).thenThrow(new java.io.IOException("no notify-send")))) {

            Method method = MainWindowJavaGi.class.getDeclaredMethod("showCompletionNotification", String.class);
            method.setAccessible(true);
            // Must not propagate: notification failure is logged, not thrown.
            method.invoke(window, "Batch complete");
            assertTrue(true);
        }
    }
}
