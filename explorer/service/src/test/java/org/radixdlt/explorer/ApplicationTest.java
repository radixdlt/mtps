package org.radixdlt.explorer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ratpack.http.Headers;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.MainClassApplicationUnderTest;

import static org.assertj.core.api.Assertions.assertThat;
import static ratpack.http.Status.BAD_REQUEST;
import static ratpack.http.Status.METHOD_NOT_ALLOWED;
import static ratpack.http.Status.OK;

public class ApplicationTest {
    private static MainClassApplicationUnderTest application;

    @BeforeClass
    public static void beforeSuite() {
        application = new MainClassApplicationUnderTest(Application.class);
    }

    @AfterClass
    public static void afterSuite() {
        application.stop();
    }

    @Test
    public void when_requesting_metrics_options__expected_headers_are_served() {
        ReceivedResponse response = application.getHttpClient().options("/api/metrics");
        Headers headers = response.getHeaders();
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(headers.get("Access-Control-Allow-Origin")).isEqualTo("*");
        assertThat(headers.get("Access-Control-Allow-Methods")).isEqualTo("GET, OPTIONS");
        assertThat(headers.get("Access-Control-Allow-Headers")).isEqualTo("Origin, Content-Type");
    }

    @Test
    public void when_requesting_transaction_options__expected_headers_are_served() {
        ReceivedResponse response = application.getHttpClient().options("/api/transactions");
        Headers headers = response.getHeaders();
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(headers.get("Access-Control-Allow-Origin")).isEqualTo("*");
        assertThat(headers.get("Access-Control-Allow-Methods")).isEqualTo("GET, OPTIONS");
        assertThat(headers.get("Access-Control-Allow-Headers")).isEqualTo("Origin, Content-Type");
    }

    @Test
    public void when_requesting_metrics__success_status_is_served() {
        String url = "/api/metrics";
        ReceivedResponse response = application.getHttpClient().get(url);
        assertThat(response.getStatus()).isEqualTo(OK);
    }

    @Test
    public void when_requesting_metrics_with_post_method__error_status_is_served() {
        String url = "/api/metrics";
        ReceivedResponse response = application.getHttpClient().post(url);
        assertThat(response.getStatus()).isEqualTo(METHOD_NOT_ALLOWED);
    }

    @Test
    public void when_requesting_transactions_with_valid_address__success_status_is_served() {
        String url = "/api/transactions/1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i";
        ReceivedResponse response = application.getHttpClient().get(url);
        assertThat(response.getStatus()).isEqualTo(OK);
    }

    @Test
    public void when_requesting_transactions_with_invalid_address__error_status_is_served() {
        String url = "/api/transactions/invalid";
        ReceivedResponse response = application.getHttpClient().get(url);
        assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
    }

    @Test
    public void when_requesting_transactions_with_negative_page__error_status_is_served() {
        String url = "/api/transactions/1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i?page=-1";
        ReceivedResponse response = application.getHttpClient().get(url);
        assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
    }

    @Test
    public void when_requesting_transactions_with_invalid_page__error_status_is_served() {
        String url = "/api/transactions/1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i?page=non_numeric";
        ReceivedResponse response = application.getHttpClient().get(url);
        assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
    }

}
