package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.gnome.gtk.GtkBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the UI-definition length passed to GtkBuilder.
 *
 * <p>
 * GtkBuilder.fromString / addFromString expect a NATIVE BYTE length, but
 * java-gi marshals Java strings to NUL-terminated UTF-8 native buffers. For
 * UI files containing multi-byte characters (e.g. the warning sign and
 * multiplication sign in settings_dialog.ui), {@code String.length()} counts
 * UTF-16 chars and under-reports the byte length, truncating the XML that GTK
 * parses. The dialogs must pass the UTF-8 byte length instead.
 * </p>
 *
 * <p>
 * Note on test strategy (documented per task requirements): the real
 * settings_dialog.ui cannot be parsed headless because GtkBuilder instantiation
 * of any GtkWidget segfaults without a display connection (verified
 * empirically). These tests therefore exercise the extracted length helper
 * with the real resource bytes plus a widget-free UI definition, which does
 * parse headless.
 * </p>
 */
@DisplayName("UI definition byte length for GtkBuilder")
class UiDefinitionByteLengthTest {

    private static final String NON_ASCII = "\u26A0\uFE0F\u00D7"; // warning sign + variation selector + multiplication sign

    @Test
    @DisplayName("Byte length equals the UTF-8 encoded byte count for multi-byte strings")
    void uiByteLength_matchesUtf8EncodedLength() {
        assertEquals(NON_ASCII.getBytes(StandardCharsets.UTF_8).length,
                SettingsDialogJavaGi.uiByteLength(NON_ASCII));
    }

    @Test
    @DisplayName("Byte length differs from the UTF-16 char count for multi-byte strings")
    void uiByteLength_differsFromCharCountForNonAscii() {
        assertTrue(SettingsDialogJavaGi.uiByteLength(NON_ASCII) != NON_ASCII.length(),
                "char counting is exactly the truncation bug: " + NON_ASCII.length() + " chars vs "
                        + NON_ASCII.getBytes(StandardCharsets.UTF_8).length + " bytes");
    }

    @Test
    @DisplayName("Byte length equals the char count for pure ASCII (why main_window.ui works today)")
    void uiByteLength_equalsCharCountForAscii() {
        String ascii = "<interface><requires lib=\"gtk\" version=\"4.0\"/></interface>";
        assertEquals(ascii.length(), SettingsDialogJavaGi.uiByteLength(ascii));
    }

    @Test
    @DisplayName("The real settings_dialog.ui resource has more bytes than chars")
    void settingsDialogResource_byteLengthExceedsCharLength() throws IOException {
        byte[] bytes = readResource("/ui/settings_dialog.ui");
        String content = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(bytes.length > content.length(),
                "resource must contain multi-byte characters; otherwise this guard no longer protects against char counting");

        assertEquals(bytes.length, SettingsDialogJavaGi.uiByteLength(content),
                "the helper must report the exact native byte length of the real UI resource");
    }

    @Test
    @DisplayName("GtkBuilder parses a non-ASCII UI definition with the computed byte length")
    void gtkBuilder_parsesNonAsciiUiWithByteLength() {
        // Widget-free UI definition (parses headless) with multi-byte content
        String ui = "<interface><!-- " + NON_ASCII + " --><requires lib=\"gtk\" version=\"4.0\"/></interface>";

        assertDoesNotThrow(() -> {
            GtkBuilder builder = new GtkBuilder();
            builder.addFromString(ui, SettingsDialogJavaGi.uiByteLength(ui));
        });
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = SettingsDialogJavaGi.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return in.readAllBytes();
        }
    }
}
