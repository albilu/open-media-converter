package org.omc.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.gnome.glib.GLib;
import org.gnome.gtk.ComboBoxText;
import org.gnome.gtk.DropDown;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.omc.core.ConfigurationManager;
import org.omc.controller.StateManager;
import org.omc.model.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual GTK widgets; run under Xvfb with an isolated user.home. */
@EnabledIfSystemProperty(named = "omc.nativeGtk", matches = "true")
class NativeFeatureWorkflowTest {
    @Test
    void presetsContextActionsAndWindowStateSurviveTheNativeWorkflow() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        MainApplicationJavaGi app = new MainApplicationJavaGi();
        app.onActivate(() -> GLib.timeoutAdd(0, 800, () -> {
            try {
                var controller = app.getController();
                var manager = controller.getSettingsManager();
                var video = VideoSettings.builder().outputFormat(FileFormat.WEBM).codec("libvpx-vp9").crf(31).build();
                var audio = AudioSettings.builder().outputFormat(FileFormat.FLAC).codec("flac").sampleRate(88200).channels(6).build();
                var image = ImageSettings.builder().outputFormat(FileFormat.WEBP).resolution(new Resolution(40, 20))
                        .resizeMode(ResizeMode.FILL).rotation(ImageRotation.CLOCKWISE_90).flip(ImageFlip.HORIZONTAL).build();
                var document = DocumentSettings.builder().outputFormat(FileFormat.HTML).marginTop(12)
                        .generateTableOfContents(true).build();
                manager.addSectionPreset(SectionPreset.forVideo("Native video", "", video, false));
                manager.addSectionPreset(SectionPreset.forAudio("Native audio", "", audio, false));
                manager.addSectionPreset(SectionPreset.forImage("Native image", "", image, false));
                manager.addSectionPreset(SectionPreset.forDocument("Native document", "", document, false));
                manager.addSectionPreset(SectionPreset.forImage("Native A", "", ImageSettings.builder().quality(30).build(), false));
                manager.addSectionPreset(SectionPreset.forImage("Native-A", "", ImageSettings.builder().quality(90).build(), false));
                var window = app.getMainWindow();
                var dialog = new SettingsDialogJavaGi(window, controller.getCurrentSettings(), manager, controller);
                dialog.setAvailableCategories(controller.getAvailableCategories());
                dialog.showDialog();
                PresetsBySection presets = manager.loadPresetsBySection();
                ((DropDown) field(dialog, "videoPresetCombo")).setSelected(index(presets.videoPresets(), "Native video"));
                ((DropDown) field(dialog, "audioPresetCombo")).setSelected(index(presets.audioPresets(), "Native audio"));
                ((ComboBoxText) field(dialog, "imagePresetCombo")).setActive(index(presets.imagePresets(), "Native image"));
                ((ComboBoxText) field(dialog, "docPresetCombo")).setActive(index(presets.documentPresets(), "Native document"));
                ConversionSettings actual = dialog.getConversionSettings();
                assertEquals(video, actual.videoSettings());
                assertEquals(audio, actual.audioSettings());
                assertEquals(image, actual.imageSettings());
                assertEquals(document, actual.documentSettings());
                for (ResizeMode mode : ResizeMode.values()) {
                    ((DropDown) field(dialog, "resizeModeDropdown")).setSelected(mode.ordinal());
                    assertEquals(mode, dialog.getConversionSettings().imageSettings().resizeMode());
                }
                dialog.closeDialog();

                Path input = Files.writeString(Path.of(System.getProperty("user.home"), "native.png"), "native fixture");
                controller.addFiles(List.of(input));
                window.updateFileList();
                window.triggerSelectAll();
                String id = controller.getFileList().getFirst().id();
                var buildMenu = MainWindowJavaGi.class.getDeclaredMethod("buildApplyPresetSubmenu", org.gnome.gio.Menu.class, List.class);
                buildMenu.setAccessible(true);
                var menu = new org.gnome.gio.Menu();
                buildMenu.invoke(window, menu, List.of(id));
                var submenu = menu.getItemLink(0, "submenu");
                java.util.Set<String> actions = new java.util.HashSet<>();
                for (int i = 0; i < submenu.getNItems(); i++) {
                    String label = submenu.getItemAttributeValue(i, "label", null).getString(null);
                    String action = submenu.getItemAttributeValue(i, "action", null).getString(null);
                    assertTrue(actions.add(action), "Preset actions must be distinct");
                    window.lookupAction(action.substring(4)).activate(null);
                    assertEquals(label, controller.getFile(id).orElseThrow().settingsOverride().presetName());
                }
                var fileList = field(window, "fileListView");
                var indicator = FileListView.class.getDeclaredMethod("resolveOutputFormat", ConversionFile.class);
                indicator.setAccessible(true);
                assertEquals("Native-A", indicator.invoke(fileList, controller.getFile(id).orElseThrow()));
                AtomicReference<String> admissionThread = new AtomicReference<>();
                Path asynchronousInput = Files.writeString(Path.of(System.getProperty("user.home"), "asynchronous.png"), "different fixture");
                app.getDependencyFactory().getFileManager().addEventListener(event -> {
                    if (event.getFile() != null && event.getFile().path().equals(asynchronousInput)) {
                        admissionThread.set(Thread.currentThread().getName());
                    }
                });
                window.addFilesAsync(List.of(asynchronousInput));
                window.setDefaultSize(1234, 876);
                GLib.timeoutAdd(0, 800, () -> {
                    try {
                        assertEquals("file-admission", admissionThread.get());
                        assertEquals(2, controller.getFileList().size());
                    } catch (AssertionError e) {
                        failure.set(e);
                    }
                    window.close();
                    return false;
                });
            } catch (AssertionError | RuntimeException | ReflectiveOperationException | java.io.IOException
                    | org.omc.exception.FileOperationException e) {
                failure.set(e);
                app.getMainWindow().close();
            }
            return false;
        }));
        assertEquals(0, app.run(new String[0]));
        if (failure.get() != null) throw new AssertionError("Native workflow failed", failure.get());
        var state = new StateManager(new ConfigurationManager()).loadState();
        assertEquals(1234, state.windowState().width());
        assertEquals(876, state.windowState().height());
        assertTrue(state.sessionState().pendingFiles().getFirst().hasCustomSettings());
    }

    private static int index(List<SectionPreset> presets, String name) {
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).name().equals(name)) return i + 1;
        throw new AssertionError("Missing preset " + name);
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
