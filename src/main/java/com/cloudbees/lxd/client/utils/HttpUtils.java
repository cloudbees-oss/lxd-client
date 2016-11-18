package com.cloudbees.lxd.client.utils;

import com.cloudbees.lxd.client.Config;
import com.cloudbees.lxd.client.utils.unix.UnixSocketFactory;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class HttpUtils {
    public static OkHttpClient createHttpClient(final Config config) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

        // Follow any redirects
        httpClientBuilder.followRedirects(true);
        httpClientBuilder.followSslRedirects(true);

        // max timeout determined for /wait
        httpClientBuilder.readTimeout(35, TimeUnit.SECONDS);

        if (config.useUnixTransport()) {
            UnixSocketFactory socketFactory = new UnixSocketFactory(config.getUnixSocketPath());
            httpClientBuilder.socketFactory(socketFactory);
        } else {
            // httpClientBuilder.sslSocketFactory(SSLUtils.getSocketFactory(config), SSLUtils.getTrustManager(config));
        }

        return httpClientBuilder.build();
    }
}
