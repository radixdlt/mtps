package org.radixdlt.explorer.helper;

import io.reactivex.Single;
import org.radixdlt.explorer.helper.model.HttpResponseData;

/**
 * Offers a set of convenience methods for requesting data from a remote
 * server and parsing it into convenient data objects.
 */
public class DataHelper {
    private final HttpClient httpClient;
    private final JsonParser jsonParser;

    /**
     * Creates a new instance of this helper class.
     *
     * @param httpClient The HTTP client to use when requesting data.
     * @param jsonParser The JSON parser to use when parsing data.
     */
    public DataHelper(HttpClient httpClient, JsonParser jsonParser) {
        this.httpClient = httpClient;
        this.jsonParser = jsonParser;
    }

    /**
     * Requests public data from a remote host, parsing it into a Java
     * object if it's a JSON resource, or delivering an exception.
     *
     * @param resultType The type of the resulting object.
     * @param host       The host name, optionally including the scheme.
     * @param path       The path to the resource to request.
     * @param arguments  The arguments to inject in the path.
     * @param <T>        The result type definition.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong, including
     * mismatching content types.
     */
    public <T> Single<T> getData(Class<T> resultType, String host, String path, Object... arguments) {
        return httpClient
                .get(false, host, path, arguments)
                .map(data -> jsonParser.fromJson(data.getContent(), resultType));
    }

    /**
     * Requests protected data from a remote host, parsing it into a
     * Java object if it's a JSON resource, or delivering an exception.
     *
     * @param resultType The type of the resulting object.
     * @param host       The host name, optionally including the scheme.
     * @param path       The path to the resource to request.
     * @param arguments  The arguments to inject in the path.
     * @param <T>        The result type definition.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong, including
     * mismatching content types.
     */
    public <T> Single<T> getDataAuthorized(Class<T> resultType, String host, String path, Object... arguments) {
        return httpClient
                .get(true, host, path, arguments)
                .map(data -> jsonParser.fromJson(data.getContent(), resultType));
    }

    /**
     * Requests public data from a remote host, delivering it as a raw
     * {@link HttpResponseData} object without any validation of content type.
     *
     * @param host      The host name, optionally including the scheme.
     * @param path      The path to the resource to request.
     * @param arguments The arguments to inject in the path.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong.
     */
    public Single<HttpResponseData> getRaw(String host, String path, Object... arguments) {
        return httpClient.get(false, host, path, arguments);
    }

    /**
     * Requests protected data from a remote host, delivering it as a raw
     * {@link HttpResponseData} object without any validation of content type.
     *
     * @param host      The host name, optionally including the scheme.
     * @param path      The path to the resource to request.
     * @param arguments The arguments to inject in the path.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong.
     */
    public Single<HttpResponseData> getRawAuthorized(String host, String path, Object... arguments) {
        return httpClient.get(true, host, path, arguments);
    }

    /**
     * Tries to parse a given JSON string into a Java object.
     *
     * @param json       The JSON to parse.
     * @param resultType The Java class describing the result.
     * @param subTypes   The optional result sub types.
     * @param <T>        The type of the result class.
     * @return A Java object representing the given JSON.
     */
    public <T> T parse(String json, Class<T> resultType, Class<?>... subTypes) {
        return jsonParser.fromJson(json, resultType, subTypes);
    }
}
