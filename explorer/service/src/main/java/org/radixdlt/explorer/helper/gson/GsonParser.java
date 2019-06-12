package org.radixdlt.explorer.helper.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.radixdlt.explorer.helper.JsonParser;

/**
 * Implements the {@link JsonParser} interface with the Google Gson JSON
 * parser library.
 */
public class GsonParser implements JsonParser {
    private final Gson gson;

    /**
     * Creates a new instance of this JSON parser implementation.
     */
    public GsonParser() {
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapterFactory(new GsonAdapterFactory())
                .create();
    }

    @Override
    public <T> T fromJson(String json, Class<T> resultType, Class<?>... subTypes) {
        if (subTypes == null || subTypes.length == 0) {
            return gson.fromJson(json, resultType);
        } else {
            TypeToken typeToken = TypeToken.getParameterized(resultType, subTypes);
            return gson.fromJson(json, typeToken.getType());
        }
    }

    @Override
    public String toJson(Object object) {
        return gson.toJson(object);
    }

}
