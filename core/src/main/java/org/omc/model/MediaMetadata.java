// filepath: src/main/java/org/omc/model/MediaMetadata.java

package org.omc.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for media-specific metadata.
 * Requirement REQ-002.2: File metadata extraction for conversion files.
 * 
 * @see VideoMetadata
 * @see AudioMetadata
 * @see ImageMetadata
 * @see DocumentMetadata
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = VideoMetadata.class, name = "video"),
        @JsonSubTypes.Type(value = AudioMetadata.class, name = "audio"),
        @JsonSubTypes.Type(value = ImageMetadata.class, name = "image"),
        @JsonSubTypes.Type(value = DocumentMetadata.class, name = "document")
})
public interface MediaMetadata {

    /**
     * Gets the type of media this metadata describes.
     * 
     * @return the format category
     */
    FormatCategory getCategory();

    /**
     * Checks if this metadata is valid and complete.
     * 
     * @return true if metadata is valid, false otherwise
     */
    boolean isValid();

    /**
     * Gets a human-readable summary of this metadata.
     * 
     * @return metadata summary string
     */
    String getSummary();
}
