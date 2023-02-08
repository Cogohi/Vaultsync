package org.avlis.vaultsync.security;

import java.util.Map;

import org.avlis.vaultsync.config.KeystoreInfo;
import org.avlis.vaultsync.config.KeystoreConfig;
import org.avlis.vaultsync.config.ReceiverConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Use the project's certificate management configuration
 * instead of repeating the information for the web sever
 */
@Component
public class WebKeystoreInit {
    private KeystoreInfo keyStoreInfo;
    private KeystoreInfo trustStoreInfo;
    private Integer serverPort;

    @Autowired
    public WebKeystoreInit(ReceiverConfig receiverConfig, KeystoreConfig keystoreConfig) {
        Map<String, KeystoreInfo> keystores = keystoreConfig.getKeystores();
        keyStoreInfo = keystores.get(receiverConfig.getKeyStore());
        trustStoreInfo = keystores.get(receiverConfig.getTrustStore());
        serverPort = receiverConfig.getServerPort();
    }

    @Bean
    @Primary
    public ServerProperties serverProperties() {
        final ServerProperties serverProperties = new ServerProperties();
        Ssl ssl = new Ssl();
        ssl.setKeyStore("file:"+keyStoreInfo.getFilePath());
        ssl.setKeyStorePassword(keyStoreInfo.getFilePassword());
        ssl.setKeyStoreType("pkcs12");
        ssl.setKeyAlias(keyStoreInfo.getKeyAlias());
        ssl.setKeyPassword(keyStoreInfo.getKeyPassword());
        ssl.setTrustStore("file:"+trustStoreInfo.getFilePath());
        ssl.setTrustStorePassword(trustStoreInfo.getFilePassword());
        ssl.setTrustStoreType("pkcs12");
        ssl.setClientAuth(ClientAuth.WANT);
        serverProperties.setSsl(ssl);
        serverProperties.setPort(serverPort);
        return serverProperties;
    }

}
