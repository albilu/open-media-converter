package org.omc.ui;

import org.omc.ui.FileDetailsDialog;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionFile;
import org.omc.model.FileSettingsOverride;
import org.omc.model.VideoSettings;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.gnome.gdk.Clipboard;
import org.gnome.glib.GLib;
import org.gnome.gtk.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileDetailsDialog.
 * 
 * Tests cover dialog creation, content building for different statuses,
 * helper methods, and edge cases.
 * 
 * IMPORTANT: These tests are disabled because they require GTK 4 display
 * initialization.
 * GTK widgets cannot be instantiated without a valid X11/Wayland display
 * connection.
 * 
 * Tests will be skipped unless:
 * - DISPLAY or WAYLAND_DISPLAY environment variable is set
 * - Active X11/Wayland session
 * - GTK 4 runtime available
 * 
 * To run these tests locally, ensure you have a display session.
 */
@Disabled("Requires GTK display - causes JVM crash in headless environment (SIGSEGV)")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FileDetailsDialogTest {

    @Mock
    private Window mockParentWindow;

    @Mock
    private Window mockDialog;

    @Mock
    private Box mockMainBox;

    @Mock
    private Box mockHeaderBox;

    @Mock
    private Box mockContentBox;

    @Mock
    private Box mockButtonBox;

    @Mock
    private Label mockFilenameLabel;

    @Mock
    private Label mockPathLabel;

    @Mock
    private Label mockSizeLabel;

    @Mock
    private Label mockFormatLabel;

    @Mock
    private Label mockMessageLabel;

    @Mock
    private Label mockSourceLabel;

    @Mock
    private Label mockOutputLabel;

    @Mock
    private Label mockDurationLabel;

    @Mock
    private Label mockToolLabel;

    @Mock
    private Label mockSizeComparisonLabel;

    @Mock
    private Label mockOutputHeaderLabel;

    @Mock
    private Label mockNoOutputLabel;

    @Mock
    private Label mockErrorLabel;

    @Mock
    private Label mockNoteLabel;

    @Mock
    private Label mockPresetLabel;

    @Mock
    private Label mockFormatInfoLabel;

    @Mock
    private ProgressBar mockProgressBar;

    @Mock
    private ProgressBar.Builder mockProgressBarBuilder;

    @Mock
    private Label.Builder mockLabelBuilder;

    @Mock
    private ScrolledWindow mockScrolledWindow;

    @Mock
    private TextView mockTextView;

    @Mock
    private Button mockCopyButton;

    @Mock
    private Button mockCloseButton;

    @Mock
    private Separator mockSeparator;

    @Mock
    private Clipboard mockClipboard;

    @Mock
    private ConversionProgress mockProgressInfo;

    private FileDetailsDialog dialog;

    private ConversionFile testFile;
    private ConversionResult testResult;

    @BeforeEach
    void setUp() {
        dialog = new FileDetailsDialog(mockParentWindow);

        // Create test data
        testFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L);
        testResult = ConversionResult.success("test-id", Path.of("/output/result.mp4"), "output text",
                Duration.ofSeconds(10), 1024L, 512L, ConversionTool.FFMPEG);

        // Mock static builders
        when(Label.builder()).thenReturn(mockLabelBuilder);
        when(mockLabelBuilder.setLabel(anyString())).thenReturn(mockLabelBuilder);
        when(mockLabelBuilder.build()).thenReturn(mockMessageLabel);

        when(ProgressBar.builder()).thenReturn(mockProgressBarBuilder);
        when(mockProgressBarBuilder.build()).thenReturn(mockProgressBar);

        // Mock GTK constructors
        mockGtkConstructors();
    }

    private void mockGtkConstructors() {
        // Mock Window constructor
        try (MockedStatic<Window> windowStatic = mockStatic(Window.class)) {
            windowStatic.when(Window::new).thenReturn(mockDialog);
        }

        // Mock Box constructors
        try (MockedStatic<Box> boxStatic = mockStatic(Box.class)) {
            boxStatic.when(() -> new Box(any(Orientation.class), anyInt())).thenReturn(mockMainBox, mockHeaderBox,
                    mockContentBox, mockButtonBox);
        }

        // Mock other GTK constructors
        try (MockedStatic<Label> labelStatic = mockStatic(Label.class)) {
            labelStatic.when(() -> new Label(anyString())).thenReturn(mockPathLabel, mockSizeLabel, mockFormatLabel,
                    mockSourceLabel, mockOutputLabel, mockDurationLabel,
                    mockToolLabel, mockSizeComparisonLabel, mockOutputHeaderLabel,
                    mockNoOutputLabel, mockErrorLabel, mockNoteLabel,
                    mockPresetLabel, mockFormatInfoLabel);
        }

        try (MockedStatic<ScrolledWindow> scrolledStatic = mockStatic(ScrolledWindow.class)) {
            scrolledStatic.when(ScrolledWindow::new).thenReturn(mockScrolledWindow);
        }

        try (MockedStatic<TextView> textViewStatic = mockStatic(TextView.class)) {
            textViewStatic.when(TextView::new).thenReturn(mockTextView);
        }

        try (MockedStatic<Button> buttonStatic = mockStatic(Button.class)) {
            buttonStatic.when(() -> Button.withLabel(anyString())).thenReturn(mockCopyButton, mockCloseButton);
        }

        try (MockedStatic<Separator> separatorStatic = mockStatic(Separator.class)) {
            separatorStatic.when(() -> new Separator(any(Orientation.class))).thenReturn(mockSeparator);
        }
    }

    // ===== Constructor Tests =====

    @Test
    void constructor_shouldAcceptValidParentWindow() {
        assertDoesNotThrow(() -> new FileDetailsDialog(mockParentWindow));
    }

    @Test
    void constructor_shouldThrowWhenParentWindowNull() {
        assertThrows(NullPointerException.class, () -> new FileDetailsDialog(null));
    }

    // ===== Helper Method Tests =====

    @Test
    void formatDuration_shouldFormatSeconds() throws Exception {
        Duration duration = Duration.ofSeconds(45);

        // Act
        String result = invokeFormatDuration(duration);

        // Assert
        assertEquals("45s", result);
    }

    @Test
    void formatDuration_shouldFormatMinutesAndSeconds() throws Exception {
        Duration duration = Duration.ofSeconds(125);

        // Act
        String result = invokeFormatDuration(duration);

        // Assert
        assertEquals("2m 5s", result);
    }

    @Test
    void formatDuration_shouldFormatHoursMinutesAndSeconds() throws Exception {
        Duration duration = Duration.ofSeconds(7265); // 2h 1m 5s

        // Act
        String result = invokeFormatDuration(duration);

        // Assert
        assertEquals("2h 1m 5s", result);
    }

    @Test
    void formatDuration_shouldHandleNull() throws Exception {
        // Act
        String result = invokeFormatDuration(null);

        // Assert
        assertEquals("N/A", result);
    }

    @Test
    void escapeMarkup_shouldEscapeSpecialCharacters() throws Exception {
        String input = "File & <test>.mp4 \"quotes\" 'apostrophes'";

        // Act
        String result = invokeEscapeMarkup(input);

        // Assert
        assertEquals("File &amp; &lt;test&gt;.mp4 &quot;quotes&quot; &apos;apostrophes&apos;", result);
    }

    @Test
    void escapeMarkup_shouldHandleNull() throws Exception {
        // Act
        String result = invokeEscapeMarkup(null);

        // Assert
        assertEquals("", result);
    }

    @Test
    void determineOutputFormat_shouldReturnCustomFormat() throws Exception {
        VideoSettings customVideoSettings = VideoSettings.builder()
                .codec("h264")
                .bitrate(1000)
                .outputFormat(FileFormat.AVI)
                .build();
        FileSettingsOverride customOverride = FileSettingsOverride.forVideo("Custom", customVideoSettings);
        ConversionFile customFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L)
                .withSettingsOverride(customOverride);

        // Act
        String result = invokeDetermineOutputFormat(customFile);

        // Assert
        assertEquals("AVI", result);
    }

    @Test
    void determineOutputFormat_shouldReturnPresetName() throws Exception {
        VideoSettings presetVideoSettings = VideoSettings.builder()
                .codec("h264")
                .bitrate(1000)
                .outputFormat(FileFormat.MP4)
                .build();
        FileSettingsOverride presetOverride = FileSettingsOverride.forVideo("High Quality", presetVideoSettings);
        ConversionFile presetFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L)
                .withSettingsOverride(presetOverride);

        // Act
        String result = invokeDetermineOutputFormat(presetFile);

        // Assert
        assertEquals("High Quality", result);
    }

    @Test
    void determineOutputFormat_shouldReturnGlobalSettings() throws Exception {
        ConversionFile globalFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L);

        // Act
        String result = invokeDetermineOutputFormat(globalFile);

        // Assert
        assertEquals("Global settings", result);
    }

    // ===== Helper Methods for Reflection =====

    private String invokeFormatDuration(Duration duration) throws Exception {
        Method method = FileDetailsDialog.class.getDeclaredMethod("formatDuration", Duration.class);
        method.setAccessible(true);
        return (String) method.invoke(dialog, duration);
    }

    private String invokeEscapeMarkup(String text) throws Exception {
        Method method = FileDetailsDialog.class.getDeclaredMethod("escapeMarkup", String.class);
        method.setAccessible(true);
        return (String) method.invoke(dialog, text);
    }

    private String invokeDetermineOutputFormat(ConversionFile file) throws Exception {
        Method method = FileDetailsDialog.class.getDeclaredMethod("determineOutputFormat", ConversionFile.class);
        method.setAccessible(true);
        return (String) method.invoke(dialog, file);
    }
}