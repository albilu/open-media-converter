// filepath: src/main/java/org/omc/model/MediaMetadataDeserializer.java

package org.omc.model;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

/**
 * Lenient deserializer for the {@link ConversionFile} {@code metadata} field.
 *
 * <p>
 * Metadata subtypes carry {@code @JsonTypeInfo}/{@code @JsonSubTypes} on the
 * {@link MediaMetadata} interface, so a typed value serializes with a
 * {@code "type"} discriminator and round-trips to the correct subtype. Session
 * files written by prior versions, however, contain flat metadata objects
 * <em>without</em> the discriminator; the subtype cannot be reconstructed
 * reliably from flat fields (different subtypes share names such as
 * {@code duration}).
 * </p>
 *
 * <p>
 * Because the field is purely informational (no production consumer dereferences
 * it without a null guard), this deserializer degrades legacy-shaped metadata
 * to {@code null} (with a debug log) instead of failing the whole
 * {@link ConversionFile}. JSON carrying a valid {@code "type"} discriminator is
 * deserialized polymorphically.
 * </p>
 *
 * <p>
 * Leniency scope (deliberate): only <em>unrecognizable</em> shapes are degraded
 * — a missing/unknown {@code "type"}. A <em>present and valid</em> type with a
 * malformed body (e.g. a numeric field holding a string) is real corruption,
 * not version skew, and propagates so the damage surfaces instead of being
 * silently discarded.
 * </p>
 *
 * @see MediaMetadata
 * @see ConversionFile#metadata()
 */
final class MediaMetadataDeserializer extends JsonDeserializer<MediaMetadata> {

    private static final Logger LOG = LoggerFactory.getLogger(MediaMetadataDeserializer.class);

    private static final String TYPE_PROPERTY = "type";

    @Override
    public MediaMetadata deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.readValueAsTree();
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() && node.has(TYPE_PROPERTY) && !node.get(TYPE_PROPERTY).isNull()) {
            try {
                return parser.getCodec().treeToValue(node, MediaMetadata.class);
            } catch (InvalidTypeIdException e) {
                // Metadata subtype written by a newer application version:
                // the field is informational, so degrade to null instead of
                // failing the whole ConversionFile
                LOG.debug("Dropping metadata with unknown '{}' value [{}]: subtype unknown to this version",
                        TYPE_PROPERTY, e.getTypeId());
                return null;
            }
        }
        LOG.debug("Dropping metadata without '{}' discriminator (legacy session format); "
                + "subtype cannot be inferred from flat fields", TYPE_PROPERTY);
        return null;
    }

    /**
     * The declared property type {@link MediaMetadata} carries
     * {@code @JsonTypeInfo}, so Jackson may wrap this deserializer with a
     * {@code TypeDeserializer}. This deserializer resolves polymorphism itself
     * (including the legacy missing-type-id fallback), so the wrapper must
     * defer here instead of failing on a missing type id.
     */
    @Override
    public MediaMetadata deserializeWithType(JsonParser parser, DeserializationContext ctxt,
            com.fasterxml.jackson.databind.jsontype.TypeDeserializer typeDeserializer) throws IOException {
        return deserialize(parser, ctxt);
    }

    @Override
    public MediaMetadata getNullValue(DeserializationContext ctxt) {
        return null;
    }
}
