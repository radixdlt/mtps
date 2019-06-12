package org.radixdlt.explorer.transactions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.radixdlt.explorer.Wrapper;
import org.radixdlt.explorer.error.IllegalAddressException;
import org.radixdlt.explorer.error.IllegalPageException;
import org.radixdlt.explorer.validation.AddressValidator;
import org.radixdlt.explorer.validation.PageValidator;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.jackson.JsonRender;
import ratpack.path.PathTokens;
import ratpack.test.exec.ExecHarness;
import ratpack.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransactionsGetHandlerTest {
    private ExecHarness execHarness;

    @Before
    public void beforeTest() {
        execHarness = ExecHarness.harness();
    }

    @After
    public void afterTest() {
        execHarness.close();
        execHarness = null;
    }

    @Test
    public void when_requesting_transactions_with_valid_address_and_page__a_valid_type_is_returned() throws Exception {
        Context mockContext = getMockContext("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i", "0");
        execHarness.run(spec -> new TransactionsGetHandler().handle(mockContext));

        ArgumentCaptor<JsonRender> captor = ArgumentCaptor.forClass(JsonRender.class);
        verify(mockContext).render(captor.capture());

        Wrapper producedData = (Wrapper) captor.getValue().getObject();
        assertThat(producedData.getType()).isEqualTo("transactions");
        assertThat(producedData.getData()).isNotNull();
    }

    @Test
    public void when_requesting_transactions_with_valid_address_and_page__metadata_is_returned() throws Exception {
        Context mockContext = getMockContext("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i", "0");
        execHarness.run(spec -> new TransactionsGetHandler().handle(mockContext));

        ArgumentCaptor<JsonRender> captor = ArgumentCaptor.forClass(JsonRender.class);
        verify(mockContext).render(captor.capture());

        Wrapper producedData = (Wrapper) captor.getValue().getObject();
        assertThat(producedData.getMeta()).isNotNull();
    }

    @Test
    public void when_requesting_transactions_with_invalid_address__exception_is_thrown() {
        Context mockContext = getMockContext("invalid", "0");
        assertThatThrownBy(() -> execHarness.run(spec ->
                new TransactionsGetHandler().handle(mockContext)))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

    @Test
    public void when_requesting_transactions_with_negative_page__exception_is_thrown() {
        Context mockContext = getMockContext("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i", "-1");
        assertThatThrownBy(() -> execHarness.run(spec ->
                new TransactionsGetHandler().handle(mockContext)))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

    @Test
    public void when_requesting_transactions_with_invalid_page__exception_is_thrown() {
        Context mockContext = getMockContext("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i", "invalid");
        assertThatThrownBy(() -> execHarness.run(spec ->
                new TransactionsGetHandler().handle(mockContext)))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

    private Context getMockContext(String address, String pageQueryParam) {
        PathTokens mockPathTokens = mock(PathTokens.class);
        when(mockPathTokens.get(anyString())).thenReturn(address);


        MultiValueMap<String, String> mockQueryParams = mock(MultiValueMap.class);
        when(mockQueryParams.getOrDefault(anyString(), anyString())).thenReturn(pageQueryParam);

        Request mockRequest = mock(Request.class);
        when(mockRequest.getQueryParams()).thenReturn(mockQueryParams);

        Context mockContext = mock(Context.class);
        when(mockContext.get(AddressValidator.class)).thenReturn(new AddressValidator());
        when(mockContext.get(PageValidator.class)).thenReturn(new PageValidator());
        when(mockContext.getPathTokens()).thenReturn(mockPathTokens);
        when(mockContext.getRequest()).thenReturn(mockRequest);

        return mockContext;
    }
}
