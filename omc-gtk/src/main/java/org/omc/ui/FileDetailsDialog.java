package org.omc.ui;

import org.omc.model.ConversionFile;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionStatus;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.util.FileUtils;

import org.gnome.gdk.Clipboard;
import org.gnome.glib.GLib;
import org.gnome.gtk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Dialog to display conversion details including tool output, metadata, and
 * status.
 * 
 * <p>
 * This dialog adapts its content based on the file's conversion status:
 * </p>
 * <ul>
 * <li><b>PENDING</b>: Shows source path, output format, and settings
 * preview</li>
 * <li><b>IN_PROGRESS</b>: Shows progress bar, real-time output, speed, and
 * ETA</li>
 * <li><b>COMPLETED</b>: Shows source/output paths, tool output, and
 * statistics</li>
 * <li><b>FAILED</b>: Shows error message and tool output for debugging</li>
 * <li><b>CANCELLED</b>: Shows partial output and cancellation timestamp</li>
 * </ul>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-FL-2.1: Double-click to view details with modal dialog</li>
 * <li>REQ-FL-2.2: Display tool output for completed/failed conversions</li>
 * <li>REQ-FL-2.3: Display progress for in-progress conversions</li>
 * <li>REQ-FL-2.4: Display placeholder for pending conversions</li>
 * </ul>
 * 
 * @see ConversionFile
 * @see ConversionResult
 * @see MainWindowJavaGi#showFileDetailsDialog(String)
 */
public class FileDetailsDialog {

    private static final Logger logger = LoggerFactory.getLogger(FileDetailsDialog.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Window parentWindow;
    private Window dialog;
    private Button copyButton;

    /**
     * Creates a new FileDetailsDialog.
     * 
     * @param parentWindow The parent window for modal behavior
     */
    public FileDetailsDialog(Window parentWindow) {
        this.parentWindow = Objects.requireNonNull(parentWindow, "Parent window cannot be null");
    }

    /**
     * Shows the dialog with file details.
     * Requirement REQ-FL-2.1: Show conversion details dialog.
     * 
     * @param file   The conversion file to display details for
     * @param result The conversion result (nullable for pending/in-progress files)
     */
    public void show(ConversionFile file, ConversionResult result) {
        if (file == null) {
            logger.warn("Cannot show dialog: file is null");
            return;
        }

        GLib.idleAdd(0, () -> {
            try {
                buildDialog(file, result);
                dialog.present();
                logger.debug("Showing details dialog for file: {}", file.id());
            } catch (Exception e) {
                logger.error("Error showing file details dialog", e);
                ErrorDialog.showError(parentWindow, "Error", "Failed to display file details: " + e.getMessage());
            }
            return false;
        });
    }

    /**
     * Builds the dialog window and assembles all sections.
     * Requirement REQ-FL-2.1: Dialog structure and layout.
     * 
     * @param file   The conversion file
     * @param result The conversion result (nullable)
     */
    private void buildDialog(ConversionFile file, ConversionResult result) {
        dialog = new Window();
        dialog.setTitle(file.fileName() + " - Conversion Details");
        dialog.setTransientFor(parentWindow);
        dialog.setModal(false);
        dialog.setDefaultSize(700, 500);
        dialog.setResizable(true);

        // Main layout container
        Box mainBox = new Box(Orientation.VERTICAL, 12);
        mainBox.setMarginTop(12);
        mainBox.setMarginBottom(12);
        mainBox.setMarginStart(12);
        mainBox.setMarginEnd(12);

        // Header section with file information
        mainBox.append(buildHeaderSection(file));

        // Separator
        mainBox.append(new Separator(Orientation.HORIZONTAL));

        // Content section (varies by status)
        Widget contentSection = buildContentSection(file, result);
        mainBox.append(contentSection);

        // Footer with action buttons
        mainBox.append(buildFooterSection(result));

        dialog.setChild(mainBox);
    }

    /**
     * Builds the header section with file metadata.
     * Requirement REQ-FL-2.1: Display file information.
     * 
     * @param file The conversion file
     * @return Widget containing header information
     */
    private Widget buildHeaderSection(ConversionFile file) {
        Box headerBox = new Box(Orientation.VERTICAL, 6);

        // Filename (bold, large)
        Label filenameLabel = Label.builder().setLabel("").build();
        filenameLabel.setMarkup("<span size='large' weight='bold'>" +
                escapeMarkup(file.fileName()) + "</span>");
        filenameLabel.setXalign(0);
        headerBox.append(filenameLabel);

        // File path
        Label pathLabel = new Label("Source: " + file.path().toString());
        pathLabel.setXalign(0);
        pathLabel.setSelectable(true);
        pathLabel.addCssClass("dim-label");
        headerBox.append(pathLabel);

        // File size
        String sizeFormatted = FileUtils.formatFileSize(file.size());
        Label sizeLabel = new Label("Size: " + sizeFormatted);
        sizeLabel.setXalign(0);
        sizeLabel.addCssClass("dim-label");
        headerBox.append(sizeLabel);

        // Input format
        Label formatLabel = new Label("Format: " + file.format().name());
        formatLabel.setXalign(0);
        formatLabel.addCssClass("dim-label");
        headerBox.append(formatLabel);

        return headerBox;
    }

    /**
     * Builds the content section based on file status.
     * Requirement REQ-FL-2.1: Status-based content display.
     * 
     * @param file   The conversion file
     * @param result The conversion result (nullable)
     * @return Widget containing status-specific content
     */
    private Widget buildContentSection(ConversionFile file, ConversionResult result) {
        return switch (file.status()) {
            case PENDING -> buildPendingContent(file);
            case IN_PROGRESS -> buildInProgressContent(file);
            case COMPLETED -> buildCompletedContent(file, result);
            case FAILED -> buildFailedContent(file, result);
            case CANCELLED -> buildCancelledContent(file, result);
        };
    }

    /**
     * Builds content for pending files.
     * Requirement REQ-FL-2.4: Display placeholder for pending conversions.
     * 
     * @param file The conversion file
     * @return Widget with pending content
     */
    private Widget buildPendingContent(ConversionFile file) {
        ScrolledWindow scrolledWindow = new ScrolledWindow();
        scrolledWindow.setVexpand(true);
        scrolledWindow.setMinContentHeight(200);

        Box contentBox = new Box(Orientation.VERTICAL, 12);
        contentBox.setMarginTop(12);
        contentBox.setMarginBottom(12);
        contentBox.setMarginStart(12);
        contentBox.setMarginEnd(12);

        // Pending message
        Label messageLabel = Label.builder().setLabel("").build();
        messageLabel.setMarkup("<span size='large'>⏳ This file has not been converted yet</span>");
        messageLabel.setXalign(0);
        contentBox.append(messageLabel);

        // Source path
        Label sourceLabel = new Label("Source: " + file.path().toString());
        sourceLabel.setXalign(0);
        sourceLabel.setSelectable(true);
        sourceLabel.setWrap(true);
        contentBox.append(sourceLabel);

        // Output format info
        String outputFormat = determineOutputFormat(file);
        Label formatLabel = new Label("Output format will be: " + outputFormat);
        formatLabel.setXalign(0);
        contentBox.append(formatLabel);

        // Settings preview (if custom settings)
        if (file.hasCustomSettings() && file.settingsOverride().presetName() != null) {
            Label presetLabel = new Label("Preset: " + file.settingsOverride().presetName());
            presetLabel.setXalign(0);
            contentBox.append(presetLabel);
        }

        scrolledWindow.setChild(contentBox);
        return scrolledWindow;
    }

    /**
     * Builds content for in-progress files.
     * Requirement REQ-FL-2.3: Display progress for in-progress conversions.
     * 
     * @param file The conversion file
     * @return Widget with progress information
     */
    private Widget buildInProgressContent(ConversionFile file) {
        ScrolledWindow scrolledWindow = new ScrolledWindow();
        scrolledWindow.setVexpand(true);
        scrolledWindow.setMinContentHeight(200);

        Box contentBox = new Box(Orientation.VERTICAL, 12);
        contentBox.setMarginTop(12);
        contentBox.setMarginBottom(12);
        contentBox.setMarginStart(12);
        contentBox.setMarginEnd(12);

        // In-progress message
        Label messageLabel = Label.builder().setLabel("").build();
        messageLabel.setMarkup("<span size='large'>⚙️ Conversion in progress...</span>");
        messageLabel.setXalign(0);
        contentBox.append(messageLabel);

        // Progress bar
        ProgressBar progressBar = new ProgressBar();
        progressBar.setFraction(file.progress() / 100.0);
        progressBar.setText(file.progress() + "%");
        progressBar.setShowText(true);
        contentBox.append(progressBar);

        // Progress details
        ConversionProgress progressInfo = file.progressInfo();
        if (progressInfo != null) {
            // Conversion speed (using formatSpeed for display)
            String speedStr = progressInfo.formatSpeed();
            Label speedLabel = new Label("Speed: " + speedStr);
            speedLabel.setXalign(0);
            contentBox.append(speedLabel);

            // Estimated time remaining
            Duration eta = progressInfo.estimatedTimeRemaining();
            if (eta != null && !eta.isZero() && !eta.isNegative()) {
                Label etaLabel = new Label("Estimated time remaining: " + formatDuration(eta));
                etaLabel.setXalign(0);
                contentBox.append(etaLabel);
            }
        }

        // Note about real-time output
        Label noteLabel = new Label("Tool output will be available after conversion completes.");
        noteLabel.setXalign(0);
        noteLabel.addCssClass("dim-label");
        contentBox.append(noteLabel);

        scrolledWindow.setChild(contentBox);
        return scrolledWindow;
    }

    /**
     * Builds content for completed files.
     * Requirement REQ-FL-2.2: Display tool output for completed conversions.
     * 
     * @param file   The conversion file
     * @param result The conversion result
     * @return Widget with completion details and tool output
     */
    private Widget buildCompletedContent(ConversionFile file, ConversionResult result) {
        Box container = new Box(Orientation.VERTICAL, 12);
        container.setVexpand(true);

        // Metadata section
        Box metadataBox = new Box(Orientation.VERTICAL, 6);

        // Success message
        Label messageLabel = Label.builder().setLabel("").build();
        messageLabel.setMarkup("<span size='large'>✓ Conversion completed successfully</span>");
        messageLabel.setXalign(0);
        metadataBox.append(messageLabel);

        if (result != null) {
            // Source path
            Label sourceLabel = new Label("Source: " + file.path().toString());
            sourceLabel.setXalign(0);
            sourceLabel.setSelectable(true);
            sourceLabel.setWrap(true);
            metadataBox.append(sourceLabel);

            // Output path
            result.outputPath().ifPresent(outputPath -> {
                Label outputLabel = new Label("Output: " + outputPath.toString());
                outputLabel.setXalign(0);
                outputLabel.setSelectable(true);
                outputLabel.setWrap(true);
                metadataBox.append(outputLabel);
            });

            // Duration
            Duration conversionTime = result.conversionTime();
            if (conversionTime != null) {
                Label durationLabel = new Label("Duration: " + formatDuration(conversionTime));
                durationLabel.setXalign(0);
                metadataBox.append(durationLabel);
            }

            // Tool used
            ConversionTool toolUsed = result.toolUsed();
            if (toolUsed != null) {
                Label toolLabel = new Label("Tool: " + toolUsed.name());
                toolLabel.setXalign(0);
                metadataBox.append(toolLabel);
            }

            // File size comparison
            if (result.outputSize() > 0) {
                String outputSize = FileUtils.formatFileSize(result.outputSize());
                Label sizeLabel = new Label("Output size: " + outputSize);
                sizeLabel.setXalign(0);
                metadataBox.append(sizeLabel);
            }
        }

        container.append(metadataBox);

        // Tool output section
        if (result != null && result.toolOutput().isPresent()) {
            Label outputHeaderLabel = new Label("Tool Output:");
            outputHeaderLabel.setXalign(0);
            outputHeaderLabel.setMarginTop(6);
            container.append(outputHeaderLabel);

            ScrolledWindow scrolledWindow = new ScrolledWindow();
            scrolledWindow.setVexpand(true);
            scrolledWindow.setMinContentHeight(200);

            TextView textView = new TextView();
            textView.setEditable(false);
            textView.setMonospace(true);
            textView.setWrapMode(WrapMode.WORD_CHAR);
            textView.getBuffer().setText(result.toolOutput().get(), -1);

            scrolledWindow.setChild(textView);
            container.append(scrolledWindow);
        } else {
            Label noOutputLabel = new Label("No output available");
            noOutputLabel.setXalign(0);
            noOutputLabel.addCssClass("dim-label");
            container.append(noOutputLabel);
        }

        return container;
    }

    /**
     * Builds content for failed files.
     * Requirement REQ-FL-2.2: Display tool output for failed conversions.
     * 
     * @param file   The conversion file
     * @param result The conversion result
     * @return Widget with error details and tool output
     */
    private Widget buildFailedContent(ConversionFile file, ConversionResult result) {
        Box container = new Box(Orientation.VERTICAL, 12);
        container.setVexpand(true);

        // Error message section
        Box errorBox = new Box(Orientation.VERTICAL, 6);

        // Failed message
        Label messageLabel = Label.builder().setLabel("").build();
        messageLabel.setMarkup("<span size='large'>✗ Conversion failed</span>");
        messageLabel.setXalign(0);
        errorBox.append(messageLabel);

        // Error message from file or result
        String errorMessage = file.errorMessage();
        if (errorMessage == null && result != null) {
            errorMessage = result.errorMessage().orElse(null);
        }

        if (errorMessage != null) {
            Label errorLabel = new Label("Error: " + errorMessage);
            errorLabel.setXalign(0);
            errorLabel.setWrap(true);
            errorLabel.setMaxWidthChars(80);
            errorLabel.addCssClass("error");
            errorBox.append(errorLabel);
        }

        // Source path
        Label sourceLabel = new Label("Source: " + file.path().toString());
        sourceLabel.setXalign(0);
        sourceLabel.setSelectable(true);
        sourceLabel.setWrap(true);
        errorBox.append(sourceLabel);

        container.append(errorBox);

        // Tool output section
        if (result != null && result.toolOutput().isPresent()) {
            Label outputHeaderLabel = new Label("Tool Output:");
            outputHeaderLabel.setXalign(0);
            outputHeaderLabel.setMarginTop(6);
            container.append(outputHeaderLabel);

            ScrolledWindow scrolledWindow = new ScrolledWindow();
            scrolledWindow.setVexpand(true);
            scrolledWindow.setMinContentHeight(200);

            TextView textView = new TextView();
            textView.setEditable(false);
            textView.setMonospace(true);
            textView.setWrapMode(WrapMode.WORD_CHAR);
            textView.getBuffer().setText(result.toolOutput().get(), -1);

            scrolledWindow.setChild(textView);
            container.append(scrolledWindow);
        } else {
            Label noOutputLabel = new Label("No output available");
            noOutputLabel.setXalign(0);
            noOutputLabel.addCssClass("dim-label");
            container.append(noOutputLabel);
        }

        return container;
    }

    /**
     * Builds content for cancelled files.
     * Requirement REQ-FL-2.2: Display partial output for cancelled conversions.
     * 
     * @param file   The conversion file
     * @param result The conversion result
     * @return Widget with cancellation details and partial output
     */
    private Widget buildCancelledContent(ConversionFile file, ConversionResult result) {
        Box container = new Box(Orientation.VERTICAL, 12);
        container.setVexpand(true);

        // Cancellation message section
        Box messageBox = new Box(Orientation.VERTICAL, 6);

        // Cancelled message
        Label messageLabel = Label.builder().setLabel("").build();
        messageLabel.setMarkup("<span size='large'>⚠ Conversion cancelled</span>");
        messageLabel.setXalign(0);
        messageBox.append(messageLabel);

        // Source path
        Label sourceLabel = new Label("Source: " + file.path().toString());
        sourceLabel.setXalign(0);
        sourceLabel.setSelectable(true);
        sourceLabel.setWrap(true);
        messageBox.append(sourceLabel);

        // Cancellation note
        Label noteLabel = new Label("The conversion was cancelled before completion.");
        noteLabel.setXalign(0);
        noteLabel.addCssClass("dim-label");
        messageBox.append(noteLabel);

        container.append(messageBox);

        // Partial tool output section
        if (result != null && result.toolOutput().isPresent()) {
            Label outputHeaderLabel = new Label("Partial Tool Output:");
            outputHeaderLabel.setXalign(0);
            outputHeaderLabel.setMarginTop(6);
            container.append(outputHeaderLabel);

            ScrolledWindow scrolledWindow = new ScrolledWindow();
            scrolledWindow.setVexpand(true);
            scrolledWindow.setMinContentHeight(200);

            TextView textView = new TextView();
            textView.setEditable(false);
            textView.setMonospace(true);
            textView.setWrapMode(WrapMode.WORD_CHAR);
            textView.getBuffer().setText(result.toolOutput().get(), -1);

            scrolledWindow.setChild(textView);
            container.append(scrolledWindow);
        } else {
            Label noOutputLabel = new Label("No output available");
            noOutputLabel.setXalign(0);
            noOutputLabel.addCssClass("dim-label");
            container.append(noOutputLabel);
        }

        return container;
    }

    /**
     * Builds the footer section with action buttons.
     * Requirement REQ-FL-2.1: Dialog buttons (Close, Copy to Clipboard).
     * 
     * @param result The conversion result (for clipboard copy)
     * @return Widget containing footer buttons
     */
    private Widget buildFooterSection(ConversionResult result) {
        Box buttonBox = new Box(Orientation.HORIZONTAL, 6);
        buttonBox.setHalign(Align.END);
        buttonBox.setMarginTop(12);

        // Copy to Clipboard button (enabled only if tool output exists)
        boolean hasOutput = result != null && result.toolOutput().isPresent();
        if (hasOutput) {
            copyButton = Button.withLabel("_Copy to Clipboard");
            copyButton.setUseUnderline(true);
            copyButton.onClicked(() -> {
                copyToolOutputToClipboard(result);
            });
            buttonBox.append(copyButton);
            this.copyButton = copyButton;
        }

        // Close button
        Button closeButton = Button.withLabel("_Close");
        closeButton.setUseUnderline(true);
        closeButton.onClicked(() -> {
            dialog.close();
        });
        buttonBox.append(closeButton);

        return buttonBox;
    }

    /**
     * Copies tool output to system clipboard and shows confirmation.
     * Requirement REQ-FL-2.1: Copy to clipboard functionality.
     * 
     * @param result The conversion result with tool output
     */
    private void copyToolOutputToClipboard(ConversionResult result) {
        if (result == null || result.toolOutput().isEmpty()) {
            logger.warn("Cannot copy to clipboard: no tool output available");
            return;
        }

        try {
            String toolOutput = result.toolOutput().get();
            org.gnome.gdk.Clipboard clipboard = dialog.getClipboard();
            clipboard.setText(toolOutput);

            logger.info("Tool output copied to clipboard ({} characters)", toolOutput.length());

            // Show brief confirmation (could use toast in GTK 4.10+)
            showCopyConfirmation();
        } catch (Exception e) {
            logger.error("Failed to copy to clipboard", e);
            ErrorDialog.showError(parentWindow, "Copy Failed", "Failed to copy tool output to clipboard");
        }
    }

    /**
     * Shows a brief confirmation that output was copied.
     */
    private void showCopyConfirmation() {
        if (copyButton != null) {
            String originalLabel = copyButton.getLabel();
            copyButton.setLabel("Copied!");
            // Revert after 2 seconds
            GLib.timeoutAdd(GLib.PRIORITY_DEFAULT, 2000, () -> {
                copyButton.setLabel(originalLabel);
                return false; // Don't repeat
            });
        }
        logger.debug("Tool output copied to clipboard");
    }

    /**
     * Determines the output format for a file (for pending status).
     * 
     * @param file The conversion file
     * @return Output format string or "Not Set"
     */
    private String determineOutputFormat(ConversionFile file) {
        if (file.hasCustomSettings()) {
            var override = file.settingsOverride();
            FormatCategory category = file.format().getCategory();
            FileFormat outputFormat = switch (category) {
                case VIDEO -> override.videoSettings() != null ? override.videoSettings().outputFormat() : null;
                case AUDIO -> override.audioSettings() != null ? override.audioSettings().outputFormat() : null;
                case IMAGE -> override.imageSettings() != null ? override.imageSettings().outputFormat() : null;
                case DOCUMENT ->
                    override.documentSettings() != null ? override.documentSettings().outputFormat() : null;
                default -> null;
            };
            if (outputFormat != null) {
                return outputFormat.name();
            } else {
                return override.presetName() != null ? override.presetName() : "Custom settings";
            }
        }
        return "Global settings";
    }

    /**
     * Formats a duration for display.
     * 
     * @param duration The duration to format
     * @return Formatted duration string (e.g., "1m 23s")
     */
    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "N/A";
        }

        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Escapes markup characters for GTK labels.
     * 
     * @param text The text to escape
     * @return Escaped text safe for markup
     */
    private String escapeMarkup(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
