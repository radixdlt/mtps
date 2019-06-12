package org.radixdlt.explorer.error;

import ratpack.error.ClientErrorHandler;
import ratpack.handling.Context;
import ratpack.http.Response;

import static ratpack.http.HttpMethod.GET;
import static ratpack.http.HttpMethod.OPTIONS;

/**
 * Offers means of graceful handling of runtime errors.
 */
public class ExplorerClientErrorHandler implements ClientErrorHandler {

    @Override
    public void error(Context context, int statusCode) {
        Response response = context.getResponse();
        response.status(statusCode);

        if (statusCode == 405) {
            response.getHeaders()
                    .set("Allow", GET.getName() + "," + OPTIONS.getName());
        }

        response.send();
    }

}
