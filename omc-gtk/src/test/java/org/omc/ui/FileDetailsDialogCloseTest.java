package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gnome.glib.GLib;
import org.gnome.glib.SourceFunc;
import org.gnome.gtk.Box;
import org.gnome.gtk.Button;
import org.gnome.gtk.Label;
import org.gnome.gtk.ScrolledWindow;
import org.gnome.gtk.Separator;
import org.gnome.gtk.Window;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;

/**
 * Regression tests for the FileDetailsDialog window-lifecycle defect: the
 * footer Close button captured the mutable {@code dialog} field, so after
 * switching files it closed the newest window instead of its own, orphaning
 * the first window.
 *
 * <p>
 * GTK widget construction is intercepted with Mockito construction mocking and
 * {@link GLib#idleAdd} callbacks run inline, so no display is needed.
 * </p>
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class FileDetailsDialogCloseTest {

    /**
     * Intercepts GTK widget construction and runs idle callbacks inline.
     * Pending files with a null result build no Copy button, so every captured
     * button is that dialog's Close button.
     */
    private static final class Fixture implements AutoCloseable {
        final List<Window> windows = new ArrayList<>();
        final List<Button.ClickedCallback> closeHandlers = new ArrayList<>();
        final Window parentWindow;

        private final MockedStatic<GLib> glib;
        private final MockedStatic<Button> buttonStatic;
        private final MockedConstruction<Window> windowConstruction;
        private final MockedConstruction<Box> boxConstruction;
        private final MockedConstruction<Label> labelConstruction;
        private final MockedConstruction<Label.Builder> labelBuilderConstruction;
        private final MockedConstruction<ScrolledWindow> scrolledWindowConstruction;
        private final MockedConstruction<Separator> separatorConstruction;

        Fixture() {
            // Plain mocks of construction-mocked classes must exist BEFORE
            // the construction mocks are registered: Mockito instantiates
            // mock() of a construction-mocked class through the real
            // constructor, which would call into native GTK and crash.
            parentWindow = mock(Window.class);
            Label builderLabel = mock(Label.class);

            glib = mockStatic(GLib.class);
            glib.when(() -> GLib.idleAdd(anyInt(), any())).thenAnswer(invocation -> {
                SourceFunc callback = invocation.getArgument(1);
                callback.run();
                return 1;
            });

            buttonStatic = mockStatic(Button.class);
            buttonStatic.when(() -> Button.withLabel(anyString())).thenAnswer(invocation -> {
                Button button = mock(Button.class);
                doAnswer(store -> {
                    closeHandlers.add(store.getArgument(0));
                    return null;
                }).when(button).onClicked(any(Button.ClickedCallback.class));
                return button;
            });

            windowConstruction = mockConstruction(Window.class, (window, context) -> windows.add(window));
            boxConstruction = mockConstruction(Box.class);
            labelConstruction = mockConstruction(Label.class);
            labelBuilderConstruction = mockConstruction(Label.Builder.class, (builder, context) -> {
                when(builder.setLabel(anyString())).thenReturn(builder);
                when(builder.build()).thenReturn(builderLabel);
            });
            scrolledWindowConstruction = mockConstruction(ScrolledWindow.class);
            separatorConstruction = mockConstruction(Separator.class);
        }

        @Override
        public void close() {
            separatorConstruction.close();
            scrolledWindowConstruction.close();
            labelBuilderConstruction.close();
            labelConstruction.close();
            boxConstruction.close();
            windowConstruction.close();
            buttonStatic.close();
            glib.close();
        }
    }

    @Test
    void closeButtonClosesItsOwnWindowAfterSwitchingFiles() {
        try (Fixture fixture = new Fixture()) {
            FileDetailsDialog detailsDialog = new FileDetailsDialog(fixture.parentWindow);
            ConversionFile fileA = ConversionFile.create(Path.of("/test/a.mp4"), FileFormat.MP4, 1024L);
            ConversionFile fileB = ConversionFile.create(Path.of("/test/b.mp4"), FileFormat.MP4, 2048L);

            detailsDialog.show(fileA, null);
            assertEquals(1, fixture.windows.size(), "show(A) must build one window");
            Window windowA = fixture.windows.get(0);

            detailsDialog.show(fileB, null);
            assertEquals(2, fixture.windows.size(), "show(B) must build a second window");
            Window windowB = fixture.windows.get(1);

            // Switching files retires A's window: only one details window per
            // dialog instance stays live.
            verify(windowA, times(1)).close();
            verify(windowB, never()).close();

            // Clicking Close on A's footer must close A's window, not B's.
            assertEquals(2, fixture.closeHandlers.size(), "each dialog must wire its own Close button");
            fixture.closeHandlers.get(0).run();

            verify(windowA, times(2)).close();
            verify(windowB, never()).close();
        }
    }

    @Test
    void closeButtonOnLatestWindowClosesThatWindow() {
        try (Fixture fixture = new Fixture()) {
            FileDetailsDialog detailsDialog = new FileDetailsDialog(fixture.parentWindow);
            ConversionFile fileA = ConversionFile.create(Path.of("/test/a.mp4"), FileFormat.MP4, 1024L);
            ConversionFile fileB = ConversionFile.create(Path.of("/test/b.mp4"), FileFormat.MP4, 2048L);

            detailsDialog.show(fileA, null);
            detailsDialog.show(fileB, null);
            Window windowB = fixture.windows.get(1);

            fixture.closeHandlers.get(1).run();

            verify(windowB, times(1)).close();
        }
    }

    @Test
    void showForSameFileTwiceRepresentsExistingWindow() {
        try (Fixture fixture = new Fixture()) {
            FileDetailsDialog detailsDialog = new FileDetailsDialog(fixture.parentWindow);
            ConversionFile file = ConversionFile.create(Path.of("/test/a.mp4"), FileFormat.MP4, 1024L);

            detailsDialog.show(file, null);
            Window windowA = fixture.windows.get(0);
            when(windowA.isVisible()).thenReturn(true);

            detailsDialog.show(file, null);

            assertEquals(1, fixture.windows.size(), "same file must reuse the open window");
            verify(windowA, times(2)).present();
            verify(windowA, never()).close();
        }
    }
}
