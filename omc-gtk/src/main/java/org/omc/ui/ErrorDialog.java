package org.omc.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.gnome.glib.GLib;
import org.gnome.gtk.Align;
import org.gnome.gtk.Box;
import org.gnome.gtk.Button;
import org.gnome.gtk.ButtonsType;
import org.gnome.gtk.DialogFlags;
import org.gnome.gtk.Expander;
import org.gnome.gtk.IconSize;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;
import org.gnome.gtk.MessageType;
import org.gnome.gtk.Orientation;
import org.gnome.gtk.ScrolledWindow;
import org.gnome.gtk.TextView;
import org.gnome.gtk.Widget;
import org.gnome.gtk.Window;
import org.gnome.gtk.WrapMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.omc.exception.ErrorCode;
import org.omc.exception.MediaConverterException;

/**
 * Error dialog helper for displaying user-friendly error messages with detailed
 * information.
 * Provides "Show Details" expander and "Copy to Clipboard" functionality.
 * 
 * <p>
 * Requirements: REQ-007.1
 * </p>
 */
public class ErrorDialog {

        private static final Logger logger = LoggerFactory.getLogger(ErrorDialog.class);

        // Map of error codes to user-friendly messages
        private static final Map<ErrorCode, String> ERROR_MESSAGES = new HashMap<>();

        static {
                // File-related errors
                ERROR_MESSAGES.put(ErrorCode.FILE_NOT_FOUND,
                                "The selected file could not be found. It may have been moved or deleted.");
                ERROR_MESSAGES.put(ErrorCode.FILE_NOT_READABLE,
                                "The file cannot be read. Please check file permissions.");
                ERROR_MESSAGES.put(ErrorCode.FILE_NOT_WRITABLE,
                                "Cannot write to the output location. Please check directory permissions.");
                ERROR_MESSAGES.put(ErrorCode.FILE_ALREADY_EXISTS,
                                "The output file already exists. Choose a different location or enable overwrite.");
                ERROR_MESSAGES.put(ErrorCode.FILE_TOO_LARGE,
                                "The file exceeds the maximum supported size.");
                ERROR_MESSAGES.put(ErrorCode.INSUFFICIENT_DISK_SPACE,
                                "Not enough disk space to complete the operation. Please free up space and try again.");
                ERROR_MESSAGES.put(ErrorCode.INVALID_FILE_FORMAT,
                                "The file format is not supported or the file is corrupted.");
                ERROR_MESSAGES.put(ErrorCode.FILE_IO_ERROR,
                                "An error occurred while reading or writing the file.");

                // Conversion-related errors
                ERROR_MESSAGES.put(ErrorCode.CONVERSION_FAILED,
                                "The conversion process failed. Please check the file and settings.");
                ERROR_MESSAGES.put(ErrorCode.CONVERSION_CANCELLED,
                                "The conversion was cancelled.");
                ERROR_MESSAGES.put(ErrorCode.CONVERSION_TIMEOUT,
                                "The conversion took too long and was stopped.");
                ERROR_MESSAGES.put(ErrorCode.INVALID_CONVERSION_SETTINGS,
                                "The conversion settings are invalid. Please review your configuration.");
                ERROR_MESSAGES.put(ErrorCode.UNSUPPORTED_CONVERSION,
                                "This format conversion is not supported. Try a different output format.");
                ERROR_MESSAGES.put(ErrorCode.OUTPUT_FILE_ERROR,
                                "Cannot create the output file. Check the output directory and permissions.");

                // Tool-related errors
                ERROR_MESSAGES.put(ErrorCode.TOOL_NOT_FOUND,
                                "Required conversion tool not found. Please ensure FFmpeg, Pandoc, or LibreOffice is installed.");
                ERROR_MESSAGES.put(ErrorCode.TOOL_EXECUTION_FAILED,
                                "The conversion tool encountered an error. Check the logs for details.");
                ERROR_MESSAGES.put(ErrorCode.TOOL_VERSION_INCOMPATIBLE,
                                "The installed tool version is not compatible. Please update your tools.");
                ERROR_MESSAGES.put(ErrorCode.TOOL_OUTPUT_PARSE_ERROR,
                                "Failed to parse conversion tool output. The tool may have produced unexpected results.");

                // Settings-related errors
                ERROR_MESSAGES.put(ErrorCode.INVALID_SETTINGS,
                                "The application settings are invalid. They will be reset to defaults.");
                ERROR_MESSAGES.put(ErrorCode.SETTINGS_LOAD_ERROR,
                                "Failed to load settings. Default settings will be used.");
                ERROR_MESSAGES.put(ErrorCode.SETTINGS_SAVE_ERROR,
                                "Failed to save settings. Changes may not persist.");

                // State persistence errors
                ERROR_MESSAGES.put(ErrorCode.STATE_LOAD_ERROR,
                                "Failed to restore previous session. Starting with a clean state.");
                ERROR_MESSAGES.put(ErrorCode.STATE_SAVE_ERROR,
                                "Failed to save application state. Your session may not be restored on next launch.");
                ERROR_MESSAGES.put(ErrorCode.STATE_MIGRATION_ERROR,
                                "Failed to migrate settings from older version. Using defaults.");
                ERROR_MESSAGES.put(ErrorCode.STATE_CORRUPTED,
                                "Application state is corrupted. Starting fresh with default settings.");

                // Validation errors
                ERROR_MESSAGES.put(ErrorCode.VALIDATION_FAILED,
                                "Validation failed. Please check your input.");
                ERROR_MESSAGES.put(ErrorCode.INVALID_INPUT,
                                "The input is invalid. Please correct it and try again.");
                ERROR_MESSAGES.put(ErrorCode.MISSING_REQUIRED_FIELD,
                                "A required field is missing. Please fill in all required information.");
                ERROR_MESSAGES.put(ErrorCode.VALUE_OUT_OF_RANGE,
                                "The value is outside the valid range.");

                // General errors
                ERROR_MESSAGES.put(ErrorCode.UNKNOWN_ERROR,
                                "An unexpected error occurred. Please try again.");
                ERROR_MESSAGES.put(ErrorCode.INTERNAL_ERROR,
                                "An internal error occurred. This may be a bug.");
                ERROR_MESSAGES.put(ErrorCode.CONFIGURATION_ERROR,
                                "Configuration error. Please check your settings.");
        }

        /**
         * Shows an error dialog for a MediaConverterException on the GTK main thread.
         * 
         * @param parent    The parent window
         * @param exception The exception to display
         */
        public static void showError(Window parent, MediaConverterException exception) {
                logger.error("Showing error dialog for exception: {}", exception.getDetailedMessage(), exception);

                String userMessage = getUserMessage(exception);
                String detailsMessage = buildDetailsMessage(exception);

                GLib.idleAdd(0, () -> {
                        showErrorDialog(parent, "Error", userMessage, detailsMessage);
                        return false;
                });
        }

        /**
         * Shows an error dialog for a generic exception on the GTK main thread.
         * 
         * @param parent    The parent window
         * @param title     The dialog title
         * @param exception The exception to display
         */
        public static void showError(Window parent, String title, Exception exception) {
                logger.error("Showing error dialog: {}", title, exception);

                String userMessage = exception.getMessage() != null ? exception.getMessage()
                                : "An unexpected error occurred";
                String detailsMessage = buildDetailsMessage(exception);

                GLib.idleAdd(0, () -> {
                        showErrorDialog(parent, title, userMessage, detailsMessage);
                        return false;
                });
        }

        /**
         * Shows an error dialog with a custom message on the GTK main thread.
         * 
         * @param parent  The parent window
         * @param title   The dialog title
         * @param message The error message
         */
        public static void showError(Window parent, String title, String message) {
                logger.error("Showing error dialog: {} - {}", title, message);

                GLib.idleAdd(0, () -> {
                        showErrorDialog(parent, title, message, null);
                        return false;
                });
        }

        /**
         * Creates and shows the error dialog with details expander and copy button.
         * Must be called on GTK main thread.
         * 
         * @param parent  The parent window
         * @param title   The dialog title
         * @param message The user-friendly error message
         * @param details The detailed error information (stack trace, etc.)
         */
        private static void showErrorDialog(Window parent, String title, String message, String details) {
                try {
                        // Create a custom window for error dialog (GTK 4 style)
                        Window dialog = new Window();
                        dialog.setTransientFor(parent);
                        dialog.setModal(true);
                        dialog.setTitle(title);
                        dialog.setDefaultSize(550, -1);
                        dialog.setResizable(false);

                        // Create content box
                        Box contentBox = new Box(Orientation.VERTICAL, 12);
                        contentBox.setMarginTop(12);
                        contentBox.setMarginBottom(12);
                        contentBox.setMarginStart(12);
                        contentBox.setMarginEnd(12);

                        // Error icon and message box
                        Box messageBox = new Box(Orientation.HORIZONTAL, 12);

                        // Error icon
                        Image errorIcon = Image.fromIconName("dialog-error");
                        errorIcon.setIconSize(IconSize.LARGE);
                        messageBox.append(errorIcon);

                        // Message label
                        Label messageLabel = new Label(message);
                        messageLabel.setWrap(true);
                        messageLabel.setMaxWidthChars(50);
                        messageLabel.setXalign(0);
                        messageBox.append(messageLabel);

                        contentBox.append(messageBox);

                        // Add details expander if details are provided
                        if (details != null && !details.isEmpty()) {
                                Expander detailsExpander = new Expander("Show Details");
                                detailsExpander.setMarginTop(12);

                                ScrolledWindow scrolledWindow = new ScrolledWindow();
                                scrolledWindow.setMinContentHeight(150);
                                scrolledWindow.setMaxContentHeight(300);
                                scrolledWindow.setHexpand(true);
                                scrolledWindow.setVexpand(true);

                                TextView detailsTextView = new TextView();
                                detailsTextView.setEditable(false);
                                detailsTextView.setMonospace(true);
                                detailsTextView.setWrapMode(WrapMode.WORD_CHAR);
                                detailsTextView.getBuffer().setText(details, -1);

                                scrolledWindow.setChild(detailsTextView);
                                detailsExpander.setChild(scrolledWindow);
                                contentBox.append(detailsExpander);
                        }

                        // Button box at bottom
                        Box buttonBox = new Box(Orientation.HORIZONTAL, 6);
                        buttonBox.setHalign(Align.END);
                        buttonBox.setMarginTop(12);

                        // Copy button (if details provided)
                        if (details != null && !details.isEmpty()) {
                                Button copyButton = Button.withLabel("_Copy Details");
                                copyButton.setUseUnderline(true);
                                final String detailsText = details;
                                copyButton.onClicked(() -> {
                                        copyToClipboard(copyButton, detailsText);
                                });
                                buttonBox.append(copyButton);
                        }

                        // OK button
                        Button okButton = Button.withLabel("_OK");
                        okButton.setUseUnderline(true);
                        okButton.onClicked(() -> {
                                dialog.close();
                        });
                        buttonBox.append(okButton);

                        contentBox.append(buttonBox);

                        // Set content and show
                        dialog.setChild(contentBox);
                        dialog.present();

                } catch (Exception e) {
                        logger.error("Error creating error dialog", e);
                        // Fallback to simple message dialog
                        showFallbackErrorDialog(parent, title, message);
                }
        }

        /**
         * Shows a simple fallback error dialog if the detailed dialog fails.
         * 
         * @param parent  The parent window
         * @param title   The dialog title
         * @param message The error message
         */
        @SuppressWarnings("deprecation")
        private static void showFallbackErrorDialog(Window parent, String title, String message) {
                try {
                        org.gnome.gtk.MessageDialog dialog = new org.gnome.gtk.MessageDialog(
                                        parent,
                                        DialogFlags.MODAL,
                                        MessageType.ERROR,
                                        ButtonsType.OK,
                                        message);
                        dialog.setTitle(title);
                        dialog.show();
                        dialog.onResponse(responseId -> dialog.destroy());
                } catch (Exception e) {
                        logger.error("Error showing fallback error dialog", e);
                }
        }

        /**
         * Copies text to the system clipboard.
         * 
         * @param widget A widget to get the clipboard from
         * @param text   The text to copy
         */
        private static void copyToClipboard(Widget widget, String text) {
                try {
                        org.gnome.gdk.Clipboard clipboard = widget.getClipboard();
                        clipboard.setText(text);
                        logger.info("Error details copied to clipboard");
                } catch (Exception e) {
                        logger.error("Failed to copy to clipboard", e);
                }
        }

        /**
         * Gets a user-friendly message for a MediaConverterException.
         * 
         * @param exception The exception
         * @return User-friendly error message
         */
        private static String getUserMessage(MediaConverterException exception) {
                ErrorCode errorCode = exception.getErrorCode();
                String userMessage = ERROR_MESSAGES.get(errorCode);

                if (userMessage == null) {
                        userMessage = exception.getMessage() != null ? exception.getMessage()
                                        : "An unexpected error occurred";
                }

                return userMessage;
        }

        /**
         * Builds a detailed error message including stack trace.
         * 
         * @param exception The exception
         * @return Detailed error message
         */
        private static String buildDetailsMessage(Exception exception) {
                StringBuilder details = new StringBuilder();

                // Add exception type and message
                details.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
                details.append("Message: ").append(exception.getMessage()).append("\n");

                // Add error code for MediaConverterException
                if (exception instanceof MediaConverterException) {
                        MediaConverterException mme = (MediaConverterException) exception;
                        details.append("Error Code: ").append(mme.getErrorCode().getCode()).append("\n");
                        if (mme.getContext() != null) {
                                details.append("Context: ").append(mme.getContext()).append("\n");
                        }
                }

                // Add stack trace
                details.append("\nStack Trace:\n");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);
                details.append(sw.toString());

                return details.toString();
        }
}
