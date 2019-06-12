package org.radixdlt.explorer;

import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.transactions.model.TransactionInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Decorates the raw data being returned to the client. This includes adding a data type notation, allowing the client
 * to determine how to treat the data. There is also the possibility to add simple key/value meta data. This could
 * typically be any paging information.
 */
public class Wrapper {
    private final String type;
    private final Object data;
    private Map<String, Object> meta = null;

    /**
     * Creates a new instance of the data wrapper.
     *
     * @param type The application specific friendly name of the data type.
     * @param data The raw data being wrapped.
     */
    private Wrapper(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Initializes a new wrapper for the given data.
     *
     * @param data The data to wrap.
     * @return An initialized wrapper, wrapping the given data
     */
    public static Wrapper of(Object data) {
        if (data instanceof Metrics) {
            return new Wrapper("metrics", data);
        } else if (data instanceof TransactionInfo[]) {
            return new Wrapper("transactions", data);
        } else {
            throw new IllegalArgumentException("Unexpected data type: " + data);
        }
    }

    /**
     * @return The type notation of the wrapped data.
     */
    public String getType() {
        return type;
    }

    /**
     * @return The wrapped raw data.
     */
    public Object getData() {
        return data;
    }

    /**
     * @return The meta data map.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Adds a single key/value pair to the meta data of this wrapper. Any duplicates will be overwritten.
     *
     * @param key   The unique meta data key.
     * @param value The corresponding value.
     * @return This data wrapper instance, allowing chained method calls.
     */
    public Wrapper addMetaData(String key, Object value) {
        if (key == null || key.isEmpty() || value == null) {
            return this;
        }

        if (meta == null) {
            meta = new HashMap<>();
        }

        meta.put(key, value);
        return this;
    }

}
