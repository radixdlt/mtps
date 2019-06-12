package org.radixdlt.explorer.helper.okhttp;

import io.reactivex.Single;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.annotations.EverythingIsNonNull;
import org.radixdlt.explorer.helper.HttpClient;
import org.radixdlt.explorer.helper.HttpClients;
import org.radixdlt.explorer.helper.model.HttpResponseData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implements the {@link HttpClient} interface with the OkHTTP3 client.
 */
public class OkHttpClient implements HttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final okhttp3.OkHttpClient httpClient;
    private final String credentials;

    /**
     * Creates a new instance of this HTTP client implementation. For
     * optimal performance and resource management it's recommended to
     * create one instance of this class and re-use it where needed.
     *
     * @param timeout  The read/write/connection timeouts in ms.
     * @param username The basic auth username to use.
     * @param password The corresponding password.
     */
    public OkHttpClient(long timeout, String username, String password) {
        httpClient = HttpClients.getSslAllTrustingClient(timeout);
        credentials = Credentials.basic(username, password, UTF_8);
    }

    @Override
    public Single<HttpResponseData> get(boolean authorize, String host, String path, Object... arguments) {
        String url = buildUrl(host, path, arguments);
        Request request = authorize
                ? buildRequest(url, "Authorization", credentials)
                : buildRequest(url);
        return get(request);
    }

    /**
     * Builds a url string from the given parameters, adding an 'https'
     * scheme if no scheme is defined in the host.
     *
     * @param host      The host name (optionally including the scheme)
     * @param path      The url path
     * @param arguments The arguments to inject in the path.
     * @return The complete url.
     */
    private String buildUrl(String host, String path, Object... arguments) {
        String url = host;

        if (path != null && !path.isEmpty()) {
            url += "/" + String.format(path, arguments);
        }

        if (URI.create(host).getScheme() == null) {
            url = "https://" + url;
        }

        return url;
    }

    /**
     * Builds an OkHttp3 request that can be passed directly to the
     * internal client.
     *
     * @param url     The complete url string.
     * @param headers The key/value pair headers.
     * @return The OkHttp3 request object.
     */
    private Request buildRequest(String url, String... headers) {
        Request.Builder request = new Request.Builder();
        request.url(url);
        if (headers != null) {
            for (int i = 0, c = headers.length; i < c; i += 2) {
                request.header(headers[i], headers[i + 1]);
            }
        }

        return request.build();
    }

    /**
     * Executes a request for a string resource.
     *
     * @param request The request to execute.
     * @return An RX Single that eventually will provide the result. The
     * single will deliver an error if something goes wrong.
     */
    private Single<HttpResponseData> get(Request request) {
        // We want to make use of of OkHttp's internal threading and
        // request queues, so we are _submitting_ (async) requests
        // rather than _executing_ (sync) them.
        return Single.create(source -> httpClient
                .newCall(request)
                .enqueue(new Callback() {
                    @Override
                    @EverythingIsNonNull
                    public void onFailure(Call call, IOException e) {
                        if (e instanceof SocketTimeoutException) {
                            LOGGER.warn("Connection timeout. Test up and running? ({})", request.url());
                            source.onSuccess(new HttpResponseData(null, null));
                        } else {
                            LOGGER.warn("Connection failed: " + request.url(), e);
                            source.onError(e);
                        }
                    }

                    @Override
                    @EverythingIsNonNull
                    public void onResponse(Call call, Response response) {
                        try {
                            HttpResponseData data = getContent(response);
                            source.onSuccess(data);
                        } catch (Exception e) {
                            LOGGER.warn("Couldn't get response: " + request.url(), e);
                            source.onError(e);
                        }
                    }
                }));
    }

    /**
     * Extracts and returns the string content along with its content
     * type from a response object.
     *
     * @param response The response to extract the data from.
     * @return The response content.
     */
    private HttpResponseData getContent(Response response) {
        try (ResponseBody body = response.body()) {
            String content = Objects.requireNonNull(body).string();
            String contentType = response.header("Content-Type");
            return new HttpResponseData(content, contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
