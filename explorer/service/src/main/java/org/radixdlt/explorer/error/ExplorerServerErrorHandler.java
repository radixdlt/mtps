package org.radixdlt.explorer.error;

import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;

import static ratpack.http.Status.BAD_REQUEST;

/**
 * Offers means of graceful handling of runtime errors.
 */
public class ExplorerServerErrorHandler implements ServerErrorHandler {

    @Override
    public void error(Context context, Throwable throwable) throws Exception {
        if (throwable instanceof IllegalAddressException) {
            context.clientError(BAD_REQUEST.getCode());
            return;
        }

        if (throwable instanceof IllegalPageException) {
            context.clientError(BAD_REQUEST.getCode());
            return;
        }

        // Huh?!
        throwable.printStackTrace();
        throw (Exception) throwable;
    }

}
