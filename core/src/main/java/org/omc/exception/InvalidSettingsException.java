package org.omc.exception;

/**
 * Exception thrown when settings validation fails.
 * Examples: invalid codec, out-of-range bitrate, incompatible settings.
 * 
 * Requirements: REQ-007.1, REQ-003.1, REQ-003.2
 */
public class InvalidSettingsException extends MediaConverterException {
    private final String settingName;
    private final Object settingValue;

    /**
     * Creates an InvalidSettingsException with setting name.
     *
     * @param message     The error message
     * @param settingName The name of the invalid setting
     */
    public InvalidSettingsException(String message, String settingName) {
        super(message, ErrorCode.INVALID_SETTINGS);
        this.settingName = settingName;
        this.settingValue = null;
    }

    /**
     * Creates an InvalidSettingsException with setting name and value.
     *
     * @param message      The error message
     * @param settingName  The name of the invalid setting
     * @param settingValue The invalid setting value
     */
    public InvalidSettingsException(String message, String settingName, Object settingValue) {
        super(message, ErrorCode.INVALID_SETTINGS);
        this.settingName = settingName;
        this.settingValue = settingValue;
    }

    /**
     * Creates an InvalidSettingsException with setting name, value, and cause.
     *
     * @param message      The error message
     * @param settingName  The name of the invalid setting
     * @param settingValue The invalid setting value
     * @param cause        The underlying cause
     */
    public InvalidSettingsException(String message, String settingName, Object settingValue, Throwable cause) {
        super(message, ErrorCode.INVALID_SETTINGS, cause);
        this.settingName = settingName;
        this.settingValue = settingValue;
    }

    /**
     * Gets the name of the invalid setting.
     *
     * @return The setting name
     */
    public String getSettingName() {
        return settingName;
    }

    /**
     * Gets the invalid setting value.
     *
     * @return The setting value, or null if not set
     */
    public Object getSettingValue() {
        return settingValue;
    }

    @Override
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s", getErrorCode().getCode(), getMessage()));

        if (settingName != null) {
            sb.append(String.format("\n  Setting: %s", settingName));
        }
        if (settingValue != null) {
            sb.append(String.format("\n  Value: %s", settingValue));
        }

        return sb.toString();
    }
}
