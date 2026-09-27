package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.gnome.gio.Notification;
import org.gnome.gio.ThemedIcon;
import org.gnome.gtk.Application;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Verifies completion handoff through Gio without spawning an external command. */
class MainWindowNotificationTest {
    @Test
    void completionUsesTheApplicationNotificationChannel() throws ReflectiveOperationException {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
        Application application = mock(Application.class);
        doReturn(application).when(window).getApplication();
        try (MockedConstruction<Notification> notifications = mockConstruction(Notification.class);
                MockedConstruction<ThemedIcon> icons = mockConstruction(ThemedIcon.class);
                MockedConstruction<ProcessBuilder> processes = mockConstruction(ProcessBuilder.class)) {
            invoke(window);
            assertEquals(1, notifications.constructed().size());
            Notification notification = notifications.constructed().getFirst();
            verify(notification).setBody("Batch complete");
            verify(notification).setIcon(icons.constructed().getFirst());
            verify(application).sendNotification("conversion-complete", notification);
            assertEquals(0, processes.constructed().size(), "Notifications must not require notify-send");
        }
    }

    @Test
    void unavailableNotificationServiceDoesNotFailTheCompletedBatch() {
        MainWindowJavaGi window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
        Application application = mock(Application.class);
        doReturn(application).when(window).getApplication();
        doThrow(new IllegalStateException("Notification channel unavailable"))
                .when(application).sendNotification(eq("conversion-complete"), any(Notification.class));
        try (MockedConstruction<Notification> notifications = mockConstruction(Notification.class);
                MockedConstruction<ThemedIcon> icons = mockConstruction(ThemedIcon.class)) {
            assertDoesNotThrow(() -> invoke(window));
            verify(application).sendNotification(eq("conversion-complete"), any(Notification.class));
        }
    }

    private static void invoke(MainWindowJavaGi window) throws ReflectiveOperationException {
        Method method = MainWindowJavaGi.class.getDeclaredMethod("showCompletionNotification", String.class);
        method.setAccessible(true);
        method.invoke(window, "Batch complete");
    }
}
