package burp.utils;

import burp.*;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class HttpUtils {
    public static CacheControl NoCache = new CacheControl.Builder().noCache().noStore().build();
    static OkHttpClient client;
    static ProxyPool proxyPool = new ProxyPool();

    static {
        client = buildClient();
    }

    /**
     * Build OkHttpClient with proxy configuration
     */
    private static OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder()
                .hostnameVerifier((hostname, session) -> true)
                .connectTimeout(3000, TimeUnit.MILLISECONDS)
                .callTimeout(500, TimeUnit.MILLISECONDS);

        // Apply proxy settings
        applyProxyConfig(builder);

        return configureToIgnoreCertificate(builder).build();
    }

    /**
     * Reload client with new proxy configuration
     */
    public static void reloadClient() {
        client = buildClient();
    }

    /**
     * Apply proxy configuration to OkHttpClient builder
     */
    private static void applyProxyConfig(OkHttpClient.Builder builder) {
        boolean proxyPoolEnabled = Config.getBoolean(Config.PROXY_POOL_ENABLED, false);
        if (proxyPoolEnabled) {
            builder.proxy(Proxy.NO_PROXY);
            return;
        }

        boolean proxyEnabled = Config.getBoolean(Config.PROXY_ENABLED, false);
        if (!proxyEnabled) {
            builder.proxy(Proxy.NO_PROXY);
            return;
        }

        String proxyHost = Config.get(Config.PROXY_HOST, "");
        String proxyPortStr = Config.get(Config.PROXY_PORT, "8080");
        String proxyType = Config.get(Config.PROXY_TYPE, "HTTP");

        if (proxyHost.isEmpty()) {
            builder.proxy(Proxy.NO_PROXY);
            return;
        }

        int proxyPort;
        try {
            proxyPort = Integer.parseInt(proxyPortStr);
        } catch (NumberFormatException e) {
            proxyPort = 8080;
        }

        Proxy.Type type = "SOCKS5".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
        builder.proxy(proxy);

        // Apply proxy authentication if configured
        String proxyUsername = Config.get(Config.PROXY_USERNAME, "");
        String proxyPassword = Config.get(Config.PROXY_PASSWORD, "");

        if (!proxyUsername.isEmpty() && !proxyPassword.isEmpty()) {
            final String username = proxyUsername;
            final String password = proxyPassword;

            builder.proxyAuthenticator((route, response) -> {
                if (response.request().header("Proxy-Authorization") != null) {
                    return null; // Authentication failed, don't retry
                }
                String credential = okhttp3.Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }
    }

    /**
     * Reload proxy pool configuration
     */
    public static void reloadProxyPool() {
        String poolText = Config.get(Config.PROXY_POOL_TEXT, "");
        proxyPool.loadFromString(poolText);
    }

    /**
     * Test proxy connectivity using Baidu domain
     */
    public static boolean testProxy(Proxy proxy, String username, String password) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(5000, TimeUnit.MILLISECONDS)
                    .hostnameVerifier((hostname, session) -> true);

            if (!username.isEmpty()) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = okhttp3.Credentials.basic(username, password);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }

            configureToIgnoreCertificate(builder);

            OkHttpClient testClient = builder.build();
            Request request = new Request.Builder()
                    .url("https://www.baidu.com")
                    .head()
                    .build();

            Response response = testClient.newCall(request).execute();
            return response.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private static OkHttpClient.Builder configureToIgnoreCertificate(OkHttpClient.Builder builder) {
        try {

            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
        }
        return builder;
    }

    public static Request.Builder GetDefaultRequest(String url) {
        int fakeFirefoxVersion = Utils.GetRandomNumber(45, 94 + Calendar.getInstance().get(Calendar.YEAR) - 2021);
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:" + fakeFirefoxVersion + ".0) Gecko/20100101 Firefox/" + fakeFirefoxVersion + ".0");
        return requestBuilder.cacheControl(NoCache);
    }

    public static String getUrlFileExt(String url) {
        String pureUrl = url.substring(0, url.contains("?") ? url.indexOf("?") : url.length());
        return (pureUrl.lastIndexOf(".") > -1 ? pureUrl.substring(pureUrl.lastIndexOf(".") + 1) : "").toLowerCase();
    }

    private static ThreadPoolExecutor executor;
    private static final ReentrantLock mainLock = new ReentrantLock();

    public static void RawRequest(IHttpService httpService, byte[] rawRequest, IRequestInfo req) {
        mainLock.lock();
        executor.submit(() -> _rawRequest(httpService, rawRequest, req));
        mainLock.unlock();
    }

    public static void waitForRequestFinish(int requestCount) throws InterruptedException {
        mainLock.lock();
        executor.shutdown();
        executor.awaitTermination(requestCount * 500L, TimeUnit.MILLISECONDS);
        resetTaskPool();
        mainLock.unlock();
    }

    private static void _rawRequest(IHttpService httpService, byte[] rawRequest, IRequestInfo req) {
        byte[] body = Arrays.copyOfRange(rawRequest, req.getBodyOffset(), rawRequest.length);
        List<String> headers = req.getHeaders();
        Request.Builder requestBuilder = new Request.Builder()
                .url(req.getUrl());
        for (int i = 1; i < headers.size(); i++) {
            HttpHeader header = new HttpHeader(headers.get(i));
            requestBuilder.header(header.Name, header.Value);
        }
        if (HttpMethod.permitsRequestBody(req.getMethod())) {
            requestBuilder.method(req.getMethod(), RequestBody.create(body));
        } else {
            requestBuilder.method(req.getMethod(), null);
        }
        requestBuilder.cacheControl(NoCache);

        ProxyPool.ProxyConfig config = null;
        try {
            boolean proxyPoolEnabled = Config.getBoolean(Config.PROXY_POOL_ENABLED, false);
            if (proxyPoolEnabled) {
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .hostnameVerifier((hostname, session) -> true)
                        .connectTimeout(3000, TimeUnit.MILLISECONDS)
                        .callTimeout(5000, TimeUnit.MILLISECONDS);

                config = proxyPool.getNextProxyConfig();
                if (config != null) {
                    final ProxyPool.ProxyConfig proxyConfig = config;
                    builder.proxy(config.toProxy());

                    if (!config.username.isEmpty()) {
                        builder.proxyAuthenticator((route, response) -> {
                            String credential = okhttp3.Credentials.basic(proxyConfig.username, proxyConfig.password);
                            return response.request().newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build();
                        });
                    }

                    configureToIgnoreCertificate(builder);
                    OkHttpClient requestClient = builder.build();
                    requestClient.newCall(requestBuilder.build()).execute();
                } else {
                    client.newCall(requestBuilder.build()).execute();
                }
            } else {
                client.newCall(requestBuilder.build()).execute();
            }
        } catch (Exception ex) {
            if (config != null) {
                proxyPool.markFailure(config);
            }
            if (ex.getMessage() != null && ex.getMessage().contains("timeout")) {
                return;
            }
            (new PrintStream(Utils.Callback.getStderr())).println(ex.getMessage());
        }
    }

    public static void resetTaskPool() {
        executor = new ThreadPoolExecutor(10, 10, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    static {
        resetTaskPool();
    }
}
