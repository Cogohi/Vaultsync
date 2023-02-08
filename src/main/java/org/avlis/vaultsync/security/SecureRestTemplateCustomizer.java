package org.avlis.vaultsync.security;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.avlis.vaultsync.config.KeystoreInfo;
import org.avlis.vaultsync.config.KeystoreConfig;
import org.avlis.vaultsync.config.SenderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SecureRestTemplateCustomizer implements RestTemplateCustomizer {

    private KeystoreInfo keyStoreInfo;
    private KeystoreInfo trustStoreInfo;
    private String sslProtocol;
    private long connectTimeout;
    private long connectionRequestTimeout;

    @Autowired
    public SecureRestTemplateCustomizer(
        SenderConfig senderConfig,
        KeystoreConfig keystoreConfig)
    {
        keyStoreInfo = keystoreConfig.getKeystores().get(senderConfig.getKeyStore());
        trustStoreInfo = keystoreConfig.getKeystores().get(senderConfig.getTrustStore());
        sslProtocol = senderConfig.getSslProtocol();
        connectTimeout = senderConfig.getConnectTimeout();
        connectionRequestTimeout = senderConfig.getConnectionRequestTimeout();
    }

    @Override
    public void customize(RestTemplate restTemplate) {
        final SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom()
                .loadKeyMaterial(new File(keyStoreInfo.getFilePath()),
                    keyStoreInfo.getFilePassword().toCharArray(),
                    keyStoreInfo.getKeyPassword().toCharArray())
                .loadTrustMaterial(new File(trustStoreInfo.getFilePath()),
                    trustStoreInfo.getFilePassword().toCharArray())
                .setProtocol(sslProtocol)
                .build();
        } catch(Exception e) {
            throw new IllegalStateException("Failed to setup client SSL context", e);
        } finally {
            // be paranoid and zero out the char[] array in properties.
            // Except... the cert can and is encouraged to be shared with the FTPS Client
            // maybe a postinit...
        }

        ConnectionConfig cc = ConnectionConfig.custom()
            .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
            .build();
        RequestConfig rc = RequestConfig.custom()
            .setConnectionRequestTimeout(connectionRequestTimeout, TimeUnit.SECONDS)
            .build();
        final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
            .setSslContext(sslContext)
            .build();
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(cc)
            .setSSLSocketFactory(sslSocketFactory)
            .build();
        final CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(rc)
            .disableRedirectHandling()
            .evictExpiredConnections()
            .build();

        final ClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        log.debug("Registered SSL keystore {} and truststore {} for client requests",
            keyStoreInfo.getFilePath(), trustStoreInfo.getFilePath());

        restTemplate.setRequestFactory(requestFactory);
    }
}
