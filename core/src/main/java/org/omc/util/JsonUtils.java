package org.omc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Utility class for JSON serialization and deserialization using Jackson.
 * Provides convenient methods for working with JSON data.
 * 
 * Requirements: REQ-005.1, REQ-005.2
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = createObjectMapper();

    // Private constructor to prevent instantiation
    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates and configures the ObjectMapper instance.
     *
     * @return Configured ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time module
        mapper.registerModule(new JavaTimeModule());

        // Pretty print by default
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Don't write dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ignore unknown properties when deserializing: persisted JSON files
        // may carry fields written by newer application versions and must
        // not fail to load (forward compatibility). Legacy preset files are
        // NOT detected by strict-parse failure anymore: SettingsManager
        // .loadPresetsBySection() inspects the JSON tree shape (legacy
        // top-level "presets" key / bare array vs current section keys) to
        // decide between normal load and backup+migration.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }

    /**
     * Gets the shared ObjectMapper instance.
     *
     * <p>
     * <strong>Contract:</strong> the returned mapper is a shared singleton.
     * Callers may use it for reads, serialization and conversion but must
     * <em>not</em> reconfigure it (enable/disable features, register modules):
     * configuration changes leak into every other JsonUtils consumer. All
     * current call sites only read/serialize, which is the supported usage.
     * The mapper is lenient on unknown properties
     * (FAIL_ON_UNKNOWN_PROPERTIES is disabled); callers that must distinguish
     * file formats do so by inspecting the JSON tree shape, not by relying on
     * strict-parse failures (see
     * {@code SettingsManager.loadPresetsBySection}).
     * </p>
     *
     * @return The ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return MAPPER;
    }

    /**
     * Serializes an object to JSON string.
     *
     * @param object The object to serialize
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return MAPPER.writeValueAsString(object);
    }

    /**
     * Serializes an object to JSON string, returning null on error.
     *
     * @param object The object to serialize
     * @return JSON string representation, or null if serialization fails
     */
    public static String toJsonSafe(Object object) {
        try {
            return toJson(object);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Deserializes JSON string to an object.
     *
     * @param json  The JSON string
     * @param clazz The target class
     * @param <T>   The type of the target object
     * @return The deserialized object
     * @throws JsonProcessingException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        return MAPPER.readValue(json, clazz);
    }

    /**
     * Deserializes JSON string to an object with TypeReference (for generics).
     *
     * @param json          The JSON string
     * @param typeReference The type reference
     * @param <T>           The type of the target object
     * @return The deserialized object
     * @throws JsonProcessingException if deserialization fails
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) throws JsonProcessingException {
        return MAPPER.readValue(json, typeReference);
    }

    /**
     * Deserializes JSON string to an object, returning null on error.
     *
     * @param json  The JSON string
     * @param clazz The target class
     * @param <T>   The type of the target object
     * @return The deserialized object, or null if deserialization fails
     */
    public static <T> T fromJsonSafe(String json, Class<T> clazz) {
        try {
            return fromJson(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Writes an object to a JSON file.
     *
     * <p>
     * The written data is forced to physical storage (fsync) before
     * returning, so callers that rename the file afterwards (the atomic
     * temp-file + move persistence pattern used by the state/settings
     * managers) cannot end up with an empty or torn file after a crash.
     * Directory fsync is not portable via {@code java.nio} and is
     * intentionally not attempted.
     * </p>
     *
     * @param object The object to write
     * @param file   The target file
     * @throws IOException if writing fails
     */
    public static void writeJsonFile(Object object, File file) throws IOException {
        MAPPER.writeValue(file, object);
        forceToDisk(file.toPath());
    }

    /**
     * Flushes the file's data (and metadata) to physical storage.
     *
     * @param file the file to flush; must already exist
     * @throws IOException if opening or forcing the channel fails
     */
    private static void forceToDisk(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Writes an object to a JSON file.
     *
     * @param object   The object to write
     * @param filePath The target file path
     * @throws IOException if writing fails
     */
    public static void writeJsonFile(Object object, String filePath) throws IOException {
        writeJsonFile(object, new File(filePath));
    }

    /**
     * Reads an object from a JSON file.
     *
     * @param file  The source file
     * @param clazz The target class
     * @param <T>   The type of the target object
     * @return The deserialized object
     * @throws IOException if reading fails
     */
    public static <T> T readJsonFile(File file, Class<T> clazz) throws IOException {
        return MAPPER.readValue(file, clazz);
    }

    /**
     * Reads an object from a JSON file.
     *
     * @param filePath The source file path
     * @param clazz    The target class
     * @param <T>      The type of the target object
     * @return The deserialized object
     * @throws IOException if reading fails
     */
    public static <T> T readJsonFile(String filePath, Class<T> clazz) throws IOException {
        return readJsonFile(new File(filePath), clazz);
    }

    /**
     * Reads an object from a JSON file with TypeReference (for generics).
     *
     * @param file          The source file
     * @param typeReference The type reference
     * @param <T>           The type of the target object
     * @return The deserialized object
     * @throws IOException if reading fails
     */
    public static <T> T readJsonFile(File file, TypeReference<T> typeReference) throws IOException {
        return MAPPER.readValue(file, typeReference);
    }

    /**
     * Reads an object from a JSON file with TypeReference (for generics).
     *
     * @param filePath      The source file path
     * @param typeReference The type reference
     * @param <T>           The type of the target object
     * @return The deserialized object
     * @throws IOException if reading fails
     */
    public static <T> T readJsonFile(String filePath, TypeReference<T> typeReference) throws IOException {
        return readJsonFile(new File(filePath), typeReference);
    }

    /**
     * Creates a deep copy of an object via JSON serialization/deserialization.
     *
     * @param object The object to clone
     * @param clazz  The class of the object
     * @param <T>    The type of the object
     * @return A deep copy of the object
     * @throws JsonProcessingException if cloning fails
     */
    public static <T> T deepCopy(T object, Class<T> clazz) throws JsonProcessingException {
        String json = toJson(object);
        return fromJson(json, clazz);
    }

    /**
     * Checks if a string is valid JSON.
     *
     * @param json The string to check
     * @return true if valid JSON
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }

        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
