package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.gnome.glib.GLib;
import org.gnome.glib.SourceFunc;
import org.gnome.gtk.Dialog;
import org.gnome.gtk.FileChooserDialog;
import org.gnome.gtk.FileFilter;
import org.gnome.gtk.MessageDialog;
import org.gnome.gtk.ResponseType;
import org.gnome.gtk.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Regression tests: a dialog destroyed via the window-manager close button
 * emits no {@code response} signal, so the registered callback would be
 * silently dropped. Each dialog must wire {@code onCloseRequest} so the
 * callback fires exactly once with the same cancellation value the existing
 * cancel semantics define (empty list / null / false), and a real response
 * must not be followed by a second callback invocation.
 *
 * <p>
 * The window is a {@code CALLS_REAL_METHODS} mock; GLib idle is stubbed to
 * run inline and the dialogs/filters are mocked constructions.
 * </p>
 */
class MainWindowDialogCloseTest {

    private MainWindowJavaGi window;

    @BeforeEach
    void setUp() {
        window = mock(MainWindowJavaGi.class, CALLS_REAL_METHODS);
    }

    private static final class CapturedDialog {
        final AtomicReference<Dialog.ResponseCallback> response = new AtomicReference<>();
        final AtomicReference<Window.CloseRequestCallback> close = new AtomicReference<>();
    }

    private static MockedStatic<GLib> idleRunsInline() {
        MockedStatic<GLib> glib = mockStatic(GLib.class);
        glib.when(() -> GLib.idleAdd(anyInt(), any(SourceFunc.class))).thenAnswer(invocation -> {
            ((SourceFunc) invocation.getArgument(1)).run();
            return 1;
        });
        return glib;
    }

    private static MockedConstruction<FileChooserDialog> capturingFileChoosers(CapturedDialog captured) {
        return mockConstruction(FileChooserDialog.class, (mock, context) -> {
            doAnswer(invocation -> {
                captured.response.set(invocation.getArgument(0));
                return null;
            }).when(mock).onResponse(any());
            doAnswer(invocation -> {
                captured.close.set(invocation.getArgument(0));
                return null;
            }).when(mock).onCloseRequest(any());
        });
    }

    private static MockedConstruction<MessageDialog> capturingMessageDialogs(CapturedDialog captured) {
        return mockConstruction(MessageDialog.class, (mock, context) -> {
            doAnswer(invocation -> {
                captured.response.set(invocation.getArgument(0));
                return null;
            }).when(mock).onResponse(any());
            doAnswer(invocation -> {
                captured.close.set(invocation.getArgument(0));
                return null;
            }).when(mock).onCloseRequest(any());
        });
    }

    private void invokeDialog(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = MainWindowJavaGi.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        method.invoke(window, args);
    }

    // ===== File chooser (cancel value: empty list) =====

    @SuppressWarnings("unchecked")
    @Test
    void fileChooser_titlebarClose_invokesCallbackOnceWithEmptyList() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<List<String>> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<FileFilter> filters = mockConstruction(FileFilter.class);
                MockedConstruction<FileChooserDialog> dialogs = capturingFileChoosers(captured)) {
            invokeDialog("showFileChooserDialog", new Class<?>[] { Consumer.class }, callback);
        }

        assertNotNull(captured.close.get(), "close-request must be wired so a titlebar close fires the callback");
        captured.close.get().run();
        verify(callback, times(1)).accept(List.of());

        captured.response.get().run(ResponseType.ACCEPT.getValue());
        verify(callback, times(1)).accept(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void fileChooser_realResponse_invokesCallbackExactlyOnce() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<List<String>> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<FileFilter> filters = mockConstruction(FileFilter.class);
                MockedConstruction<FileChooserDialog> dialogs = capturingFileChoosers(captured)) {
            invokeDialog("showFileChooserDialog", new Class<?>[] { Consumer.class }, callback);
        }

        captured.response.get().run(ResponseType.CANCEL.getValue());
        verify(callback, times(1)).accept(List.of());

        captured.close.get().run();
        verify(callback, times(1)).accept(any());
    }

    // ===== Folder chooser (cancel value: null) =====

    @SuppressWarnings("unchecked")
    @Test
    void folderChooser_titlebarClose_invokesCallbackOnceWithNull() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<String> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<FileChooserDialog> dialogs = capturingFileChoosers(captured)) {
            invokeDialog("showFolderChooserDialog", new Class<?>[] { Consumer.class }, callback);
        }

        assertNotNull(captured.close.get(), "close-request must be wired so a titlebar close fires the callback");
        captured.close.get().run();
        verify(callback, times(1)).accept(isNull());

        captured.response.get().run(ResponseType.ACCEPT.getValue());
        verify(callback, times(1)).accept(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void folderChooser_realResponse_invokesCallbackExactlyOnce() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<String> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<FileChooserDialog> dialogs = capturingFileChoosers(captured)) {
            invokeDialog("showFolderChooserDialog", new Class<?>[] { Consumer.class }, callback);
        }

        captured.response.get().run(ResponseType.CANCEL.getValue());
        verify(callback, times(1)).accept(isNull());

        captured.close.get().run();
        verify(callback, times(1)).accept(any());
    }

    // ===== Confirm dialog (cancel value: false) =====

    @SuppressWarnings("unchecked")
    @Test
    void confirmDialog_titlebarClose_invokesCallbackOnceWithFalse() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<Boolean> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<MessageDialog> dialogs = capturingMessageDialogs(captured)) {
            invokeDialog("showConfirmDialog", new Class<?>[] { String.class, String.class, Consumer.class },
                    "title", "message", callback);
        }

        assertNotNull(captured.close.get(), "close-request must be wired so a titlebar close fires the callback");
        captured.close.get().run();
        verify(callback, times(1)).accept(false);

        captured.response.get().run(ResponseType.YES.getValue());
        verify(callback, times(1)).accept(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void confirmDialog_realResponse_invokesCallbackExactlyOnce() throws Exception {
        CapturedDialog captured = new CapturedDialog();
        Consumer<Boolean> callback = mock(Consumer.class);
        try (MockedStatic<GLib> glib = idleRunsInline();
                MockedConstruction<MessageDialog> dialogs = capturingMessageDialogs(captured)) {
            invokeDialog("showConfirmDialog", new Class<?>[] { String.class, String.class, Consumer.class },
                    "title", "message", callback);
        }

        captured.response.get().run(ResponseType.YES.getValue());
        verify(callback, times(1)).accept(true);

        captured.close.get().run();
        verify(callback, times(1)).accept(any());
    }
}
