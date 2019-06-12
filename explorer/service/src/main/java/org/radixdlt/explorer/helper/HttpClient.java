package org.radixdlt.explorer.helper;

import io.reactivex.Single;
import org.radixdlt.explorer.helper.model.HttpResponseData;

/**
 * Describes the HTTP request feature.
 */
public interface HttpClient {

    /**
     * Requests string content from a remote server.
     *
     * @param authorize Whether to send basic authentication header or not.
     * @param host      The host to request.
     * @param path      The path to the resource to request.
     * @param arguments Any optional arguments to inject in the path.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong.
     */
    Single<HttpResponseData> get(boolean authorize, String host, String path, Object... arguments);

}
