// filepath: src/test/java/org/omc/model/SettingsPresetTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for SettingsPreset model.
 * Tests preset creation, validation, and immutability.
 * 
 * Requirement REQ-003.2: Settings presets for quick configuration
 */
class SettingsPresetTest {

        @Test
        void createUserPreset_ShouldCreateValidPreset() {
                // Given: Valid preset data
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                // When: Create user preset
                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "My Preset",
                                "Custom user preset",
                                settings);

                // Then: Preset should be created correctly
                assertNotNull(preset);
                assertEquals("My Preset", preset.name());
                assertEquals("Custom user preset", preset.description());
                assertEquals(settings, preset.settings());
                assertFalse(preset.builtIn());
                assertTrue(preset.createdAt() > 0);
                assertTrue(preset.isValid());
        }

        @Test
        void createUserPreset_ShouldTrimName() {
                // Given: Preset name with whitespace
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                // When: Create preset with whitespace in name
                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "  Spaced Name  ",
                                "Description",
                                settings);

                // Then: Name should be trimmed
                assertEquals("Spaced Name", preset.name());
        }

        @Test
        void createUserPreset_ShouldThrowWhenNameIsNull() {
                // Given: Null name
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                // When/Then: Should throw exception
                assertThrows(NullPointerException.class, () -> {
                        SettingsPreset.createUserPreset(null, "Description", settings);
                });
        }

        @Test
        void createUserPreset_ShouldThrowWhenNameIsEmpty() {
                // Given: Empty name
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                // When/Then: Should throw exception
                assertThrows(IllegalArgumentException.class, () -> {
                        SettingsPreset.createUserPreset("   ", "Description", settings);
                });
        }

        @Test
        void createUserPreset_ShouldThrowWhenSettingsIsNull() {
                // When/Then: Should throw exception
                assertThrows(NullPointerException.class, () -> {
                        SettingsPreset.createUserPreset("Name", "Description", null);
                });
        }

        @Test
        void createUserPreset_ShouldAllowNullDescription() {
                // Given: Valid settings, null description
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                // When: Create preset with null description
                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "Name",
                                null,
                                settings);

                // Then: Should be created successfully
                assertNotNull(preset);
                assertNull(preset.description());
                assertTrue(preset.isValid());
        }

        @Test
        void createBuiltInPreset_ShouldCreateBuiltInPreset() {
                // Given: Valid preset data
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(2)
                                .build();

                // When: Create built-in preset
                SettingsPreset preset = SettingsPreset.createBuiltInPreset(
                                "High Quality",
                                "Maximum quality preset",
                                settings);

                // Then: Preset should be marked as built-in
                assertNotNull(preset);
                assertEquals("High Quality", preset.name());
                assertEquals("Maximum quality preset", preset.description());
                assertTrue(preset.builtIn());
                assertTrue(preset.isValid());
        }

        @Test
        void isValid_ShouldReturnTrueForValidPreset() {
                // Given: Valid preset
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "Valid",
                                "Valid preset",
                                settings);

                // When/Then: Should be valid
                assertTrue(preset.isValid());
        }

        @Test
        void isValid_ShouldReturnFalseForInvalidSettings() {
                // Given: Preset with invalid settings (created via constructor)
                ConversionSettings invalidSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(100) // Invalid
                                .build();

                SettingsPreset preset = new SettingsPreset(
                                "Invalid",
                                "Invalid preset",
                                invalidSettings,
                                false,
                                System.currentTimeMillis());

                // When/Then: Should be invalid
                assertFalse(preset.isValid());
        }

        @Test
        void withSettings_ShouldCreateNewPresetWithUpdatedSettings() {
                // Given: Original preset
                ConversionSettings originalSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset original = SettingsPreset.createUserPreset(
                                "Original",
                                "Original description",
                                originalSettings);

                // When: Update settings
                ConversionSettings newSettings = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(8)
                                .build();

                SettingsPreset updated = original.withSettings(newSettings);

                // Then: Should create new preset with updated settings
                assertNotNull(updated);
                assertNotSame(original, updated);
                assertEquals(original.name(), updated.name());
                assertEquals(original.description(), updated.description());
                assertEquals(newSettings, updated.settings());
                // Timestamp should be updated or same (may be same if executed too quickly)
                assertTrue(updated.createdAt() >= original.createdAt());
        }

        @Test
        void withDescription_ShouldCreateNewPresetWithUpdatedDescription() {
                // Given: Original preset
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset original = SettingsPreset.createUserPreset(
                                "Name",
                                "Original description",
                                settings);

                long originalTimestamp = original.createdAt();

                // When: Update description
                SettingsPreset updated = original.withDescription("New description");

                // Then: Should create new preset with updated description
                assertNotNull(updated);
                assertNotSame(original, updated);
                assertEquals(original.name(), updated.name());
                assertEquals("New description", updated.description());
                assertEquals(original.settings(), updated.settings());
                assertEquals(originalTimestamp, updated.createdAt()); // Timestamp preserved
        }

        @Test
        void equals_ShouldReturnTrueForIdenticalPresets() {
                // Given: Two identical presets
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                long timestamp = System.currentTimeMillis();

                SettingsPreset preset1 = new SettingsPreset(
                                "Name",
                                "Description",
                                settings,
                                false,
                                timestamp);

                SettingsPreset preset2 = new SettingsPreset(
                                "Name",
                                "Description",
                                settings,
                                false,
                                timestamp);

                // When/Then: Should be equal
                assertEquals(preset1, preset2);
                assertEquals(preset1.hashCode(), preset2.hashCode());
        }

        @Test
        void equals_ShouldReturnFalseForDifferentPresets() {
                // Given: Two different presets
                ConversionSettings settings1 = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                ConversionSettings settings2 = ConversionSettings.builder()
                                .outputFormat(FileFormat.AVI)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(8)
                                .build();

                SettingsPreset preset1 = SettingsPreset.createUserPreset(
                                "Preset1",
                                "Description1",
                                settings1);

                SettingsPreset preset2 = SettingsPreset.createUserPreset(
                                "Preset2",
                                "Description2",
                                settings2);

                // When/Then: Should not be equal
                assertNotEquals(preset1, preset2);
        }

        @Test
        void equals_ShouldHandleSameInstance() {
                // Given: Single preset
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "Name",
                                "Description",
                                settings);

                // When/Then: Should equal itself
                assertEquals(preset, preset);
        }

        @Test
        void equals_ShouldHandleNull() {
                // Given: Preset
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "Name",
                                "Description",
                                settings);

                // When/Then: Should not equal null
                assertNotEquals(preset, null);
        }

        @Test
        void equals_ShouldHandleDifferentClass() {
                // Given: Preset and different object
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "Name",
                                "Description",
                                settings);

                // When/Then: Should not equal different class
                assertNotEquals(preset, "String object");
        }

        @Test
        void toString_ShouldIncludeAllFields() {
                // Given: Preset
                ConversionSettings settings = ConversionSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .outputDirectory(Paths.get("/tmp"))
                                .parallelConversions(4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "TestName",
                                "TestDescription",
                                settings);

                // When: Convert to string
                String str = preset.toString();

                // Then: Should contain all key information
                assertNotNull(str);
                assertTrue(str.contains("TestName"));
                assertTrue(str.contains("TestDescription"));
                assertTrue(str.contains("builtIn"));
                assertTrue(str.contains("createdAt"));
        }
}
