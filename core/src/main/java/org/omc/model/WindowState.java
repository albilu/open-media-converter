// filepath: src/main/java/org/omc/model/WindowState.java

package org.omc.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the state of the main application window.
 * Requirement REQ-005.1: Window geometry persistence.
 * 
 * Note: @JsonIgnoreProperties ensures backward compatibility when loading
 * state files from older versions that may have different field sets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WindowState {

    private final int width;
    private final int height;
    private final int x;
    private final int y;
    private final boolean maximized;
    private final boolean fullscreen;

    @JsonCreator
    public WindowState(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("x") int x,
            @JsonProperty("y") int y,
            @JsonProperty("maximized") boolean maximized,
            @JsonProperty("fullscreen") boolean fullscreen) {
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
        this.maximized = maximized;
        this.fullscreen = fullscreen;
    }

    /**
     * Creates a default window state.
     */
    public static WindowState defaultState() {
        return new WindowState(1000, 700, 100, 100, false, false);
    }

    @JsonProperty("width")
    public int width() {
        return width;
    }

    @JsonProperty("height")
    public int height() {
        return height;
    }

    @JsonProperty("x")
    public int x() {
        return x;
    }

    @JsonProperty("y")
    public int y() {
        return y;
    }

    @JsonProperty("maximized")
    public boolean maximized() {
        return maximized;
    }

    @JsonProperty("fullscreen")
    public boolean fullscreen() {
        return fullscreen;
    }

    /**
     * Validates that window state values are reasonable.
     */
    @JsonIgnore
    public boolean isValid() {
        // Width and height must be positive and reasonable
        if (width < 400 || width > 7680) { // Min 400px, max 8K width
            return false;
        }
        if (height < 300 || height > 4320) { // Min 300px, max 8K height
            return false;
        }

        // Position can be negative (multi-monitor), but not too extreme
        if (x < -7680 || x > 7680) {
            return false;
        }
        if (y < -4320 || y > 4320) {
            return false;
        }

        return true;
    }

    /**
     * Creates a copy with updated dimensions.
     */
    public WindowState withSize(int width, int height) {
        return new WindowState(width, height, x, y, maximized, fullscreen);
    }

    /**
     * Creates a copy with updated position.
     */
    public WindowState withPosition(int x, int y) {
        return new WindowState(width, height, x, y, maximized, fullscreen);
    }

    /**
     * Creates a copy with updated maximized state.
     */
    public WindowState withMaximized(boolean maximized) {
        return new WindowState(width, height, x, y, maximized, fullscreen);
    }

    /**
     * Creates a copy with updated fullscreen state.
     */
    public WindowState withFullscreen(boolean fullscreen) {
        return new WindowState(width, height, x, y, maximized, fullscreen);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WindowState that = (WindowState) o;
        return width == that.width &&
                height == that.height &&
                x == that.x &&
                y == that.y &&
                maximized == that.maximized &&
                fullscreen == that.fullscreen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, x, y, maximized, fullscreen);
    }

    @Override
    public String toString() {
        return "WindowState{" +
                "width=" + width +
                ", height=" + height +
                ", x=" + x +
                ", y=" + y +
                ", maximized=" + maximized +
                ", fullscreen=" + fullscreen +
                '}';
    }
}
