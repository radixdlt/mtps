package org.radixdlt.explorer.helper;

/**
 * Describes the JSON parsing and serialization feature.
 */
public interface JsonParser {

    /**
     * Parses a given JSON string into a Java object and returns it.
     *
     * @param json       The JSON string to parse.
     * @param resultType The Java class describing the result.
     * @param subTypes   The optional result sub types.
     * @param <T>        The result type definition.
     * @return The Java object representing the given JSON string.
     */
    <T> T fromJson(String json, Class<T> resultType, Class<?>... subTypes);

    /**
     * Serializes a given Java object into a JSON string.
     *
     * @param object The object to serialize.
     * @return The JSON string notation of the given Java object.
     */
    String toJson(Object object);

}
