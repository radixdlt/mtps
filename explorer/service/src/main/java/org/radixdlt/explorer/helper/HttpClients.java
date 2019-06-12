package org.radixdlt.explorer.helper;

import io.reactivex.Single;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.BiFunction;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Offers means of getting hold of standard configured HTTP clients.
 *
 * This code is copied from the Radix java library repository:
 * https://github.com/radixdlt/radixdlt-java
 */
public class HttpClients {
    private static final Object LOCK = new Object();
    private static OkHttpClient sslAllTrustingClient;

    private HttpClients() {
        throw new UnsupportedOperationException("This class should not be instantiated");
    }

    private static OkHttpClient createClient(long timeout, BiFunction<X509Certificate[], String, Single<Boolean>> trustManager) {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        if (!trustManager.apply(chain, authType).blockingGet()) {
                            throw new CertificateException();
                        }
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        if (!trustManager.apply(chain, authType).blockingGet()) {
                            throw new CertificateException();
                        }
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };

        try {
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> hostname.equals(session.getPeerHost()));

            builder.connectTimeout(timeout, MILLISECONDS)
                    .writeTimeout(timeout, MILLISECONDS)
                    .readTimeout(timeout, MILLISECONDS);

            return builder.build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Could not create http client: ", e);
        }
    }

    /**
     * Builds an instance of {@link OkHttpClient} to be used for secure
     * connections with self signed certificates.
     */
    public static OkHttpClient getSslAllTrustingClient(long timeout) {
        synchronized (LOCK) {
            if (sslAllTrustingClient == null) {
                sslAllTrustingClient = createClient(timeout, ((x509Certificates, s) -> Single.just(true)));
            }
            return sslAllTrustingClient;
        }
    }

}
