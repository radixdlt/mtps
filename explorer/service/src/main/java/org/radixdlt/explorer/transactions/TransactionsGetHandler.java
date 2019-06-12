package org.radixdlt.explorer.transactions;

import com.google.gson.Gson;
import io.reactivex.Single;
import org.radixdlt.explorer.Wrapper;
import org.radixdlt.explorer.transactions.model.TransactionInfo;
import org.radixdlt.explorer.validation.AddressValidator;
import org.radixdlt.explorer.validation.PageValidator;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Response;
import ratpack.rx2.RxRatpack;

import static ratpack.http.Status.BAD_REQUEST;
import static ratpack.http.Status.OK;

/**
 * Handles any transactions requests. This class knows how to validate the input data.
 */
public class TransactionsGetHandler implements Handler {

    @Override
    public void handle(Context context) throws RuntimeException {
        // Ensure we have a valid bitcoin address. Throws on error. The
        // exception is then caught by our ExplorerServerErrorHandler.
        String address = context.getPathTokens().get("address");
        AddressValidator bitcoinAddress = context.get(AddressValidator.class);
        String validAddress = bitcoinAddress.validate(address);

        // Ensure we have a valid page. Throws on error. The exception
        // is then caught by our ExplorerServerErrorHandler.
        String page = context.getRequest().getQueryParams().getOrDefault("page", "1");
        PageValidator pageValidator = context.get(PageValidator.class);
        int validPage = pageValidator.validate(page);

        TransactionsService service = context.get(TransactionsService.class);
        Single<TransactionInfo[]> single = service.getTransactionsObserver(validAddress, validPage);

        long maxAge = 10; // seconds
        RxRatpack.promise(single)
                .onError(cause -> {
                    Response response = context.getResponse();
                    response.getHeaders()
                            .set("Content-Type", "text/plain")
                            .set("Cache-Control", "max-age=" + maxAge);
                    response.status(BAD_REQUEST)
                            .send(cause.getMessage());
                })
                .then(transactions -> {
                    Wrapper wrapper = Wrapper.of(transactions)
                            .addMetaData("page", validPage);

                    String json = new Gson().toJson(wrapper);

                    Response response = context.getResponse();
                    response.getHeaders()
                            .set("Content-Type", "application/json")
                            .set("Cache-Control", "max-age=" + maxAge);
                    response.status(OK)
                            .send(json);
                });
    }

}
