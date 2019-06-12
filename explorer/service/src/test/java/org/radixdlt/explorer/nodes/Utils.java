package org.radixdlt.explorer.nodes;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offers convenience methods to manage the "mocked internet".
 */
class Utils {

    static OkHttpClient getMockClient(final String... data) {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        AtomicInteger counter = new AtomicInteger();

        int size = data.length;
        int halfSize = size / 2;
        String[][] responses = new String[halfSize][2];
        for (int i = 0, j = 0; i < halfSize && j < size; i++, j += 2) {
            responses[i][0] = data[j];
            responses[i][1] = data[j + 1];
        }

        Call mockCall = mock(Call.class);
        doAnswer(invocation -> {
            int index = counter.getAndIncrement() % responses.length;
            String content = responses[index][0];
            MediaType contentType = MediaType.get(responses[index][1]);
            ResponseBody body = ResponseBody.create(contentType, content);
            Callback callback = invocation.getArgument(0);
            callback.onResponse(mockCall, new Response.Builder()
                    .request(captor.getValue())
                    .protocol(Protocol.HTTP_1_1)
                    .addHeader("Content-Type", contentType.toString())
                    .code(200).message("OK")
                    .body(body).build());
            return null;
        }).when(mockCall).enqueue(any());

        OkHttpClient mockClient = mock(OkHttpClient.class);
        when(mockClient.newCall(captor.capture())).thenReturn(mockCall);

        return mockClient;
    }

}
