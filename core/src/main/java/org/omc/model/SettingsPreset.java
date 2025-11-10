// filepath: src/main/java/org/omc/model/SettingsPreset.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a named preset for conversion settings.
 * Presets allow users to save and quickly apply common conversion
 * configurations.
 * 
 * Requirement REQ-003.2: Format presets for common use cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SettingsPreset {

    private final String name;
    private final String description;
    private final ConversionSettings settings;
    private final boolean builtIn;
    private final long createdAt;

    @JsonCreator
    public SettingsPreset(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("settings") ConversionSettings settings,
            @JsonProperty("builtIn") boolean builtIn,
            @JsonProperty("createdAt") long createdAt) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description;
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.builtIn = builtIn;
        this.createdAt = createdAt;
    }

    /**
     * Creates a new user preset (not built-in).
     *
     * @param name        Preset name (must not be null or empty)
     * @param description Optional description of the preset
     * @param settings    Conversion settings for this preset
     * @return New settings preset
     */
    public static SettingsPreset createUserPreset(String name, String description, ConversionSettings settings) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        return new SettingsPreset(name.trim(), description, settings, false, System.currentTimeMillis());
    }

    /**
     * Creates a new built-in preset.
     *
     * @param name        Preset name
     * @param description Description of the preset
     * @param settings    Conversion settings for this preset
     * @return New built-in settings preset
     */
    public static SettingsPreset createBuiltInPreset(String name, String description, ConversionSettings settings) {
        return new SettingsPreset(name, description, settings, true, System.currentTimeMillis());
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("description")
    public String description() {
        return description;
    }

    @JsonProperty("settings")
    public ConversionSettings settings() {
        return settings;
    }

    @JsonProperty("builtIn")
    public boolean builtIn() {
        return builtIn;
    }

    @JsonProperty("createdAt")
    public long createdAt() {
        return createdAt;
    }

    /**
     * Validates that the preset has valid name and settings.
     *
     * @return true if preset is valid
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
                && settings != null && settings.isValid();
    }

    /**
     * Creates a copy with updated settings.
     *
     * @param newSettings New settings to use
     * @return New preset with updated settings
     */
    public SettingsPreset withSettings(ConversionSettings newSettings) {
        return new SettingsPreset(name, description, newSettings, builtIn, System.currentTimeMillis());
    }

    /**
     * Creates a copy with updated description.
     *
     * @param newDescription New description
     * @return New preset with updated description
     */
    public SettingsPreset withDescription(String newDescription) {
        return new SettingsPreset(name, newDescription, settings, builtIn, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SettingsPreset that = (SettingsPreset) o;
        return builtIn == that.builtIn &&
                createdAt == that.createdAt &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(settings, that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, settings, builtIn, createdAt);
    }

    @Override
    public String toString() {
        return "SettingsPreset{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", builtIn=" + builtIn +
                ", createdAt=" + createdAt +
                ", settings=" + settings +
                '}';
    }
}
