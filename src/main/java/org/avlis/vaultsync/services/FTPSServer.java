package org.avlis.vaultsync.services;

import java.io.File;
import java.util.Collections;

import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfiguration;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.avlis.vaultsync.config.KeystoreInfo;
import org.avlis.vaultsync.config.PeerConfig;
import org.avlis.vaultsync.security.CertUserManager;
import org.avlis.vaultsync.security.DenyListService;
import org.avlis.vaultsync.config.KeystoreConfig;
import org.avlis.vaultsync.config.CommonConfig;
import org.avlis.vaultsync.config.FTPSServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FTPSServer {

    private DenyListService denyListService;
    private CommonConfig commonConfig;
    private FTPSServerConfig ftpsServerConfig;
    private PeerConfig peerConfig;

    private static FtpServer server;

    @Autowired
    public FTPSServer(  CommonConfig commonConfig,
                        DenyListService denyListService,
                        FTPSServerConfig config,
                        PeerConfig peerConfig)
    {
        this.commonConfig = commonConfig;
        this.denyListService = denyListService;
        this.ftpsServerConfig = config;
        this.peerConfig = peerConfig;

        if(server == null) {
            initFtpServer();
        }
    }

    // NOTE: Split server instantiation from the constructor in case
    //       we need to add a method to reinitialize it programmatically

    private void initFtpServer() {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory factory = new ListenerFactory();

        KeystoreConfig keystoreConfig = ftpsServerConfig.getKeystoreConfig();

        // set the port of the listener
        factory.setPort(ftpsServerConfig.getPort());
        // No clue why FtpServer doesn't sanely log auth failures by default.
        // Watch the requests and responses for errors that should be logged, eg. authentication
        serverFactory.setFtplets(Collections.singletonMap("cmdErrorLog", new FTPSCommandErrorLogger()));

        // Implement FTPS - ftp over TLS/SSL
        SslConfigurationFactory ssl = new SslConfigurationFactory();

        String keyStoreId = ftpsServerConfig.getKeyStore();
        KeystoreInfo keystoreInfo = keystoreConfig.getKeystores().get(keyStoreId);
        String keyPassword = keystoreInfo.getKeyPassword();
        if(keyPassword == null || keyPassword.isEmpty()) {
            log.warn("The 'keyPassword' field for the '"+keyStoreId+"' keystore configuration is blank.  If you're getting an UnrecoverableKeyException please check.");
        }
        ssl.setKeystoreFile(new File(keystoreInfo.getFilePath()));
        ssl.setKeystorePassword(keystoreInfo.getFilePassword());
        ssl.setKeyPassword(keyPassword);
        ssl.setKeyAlias(keystoreInfo.getKeyAlias());

        // Require trusted client cert
        KeystoreInfo truststoreInfo = keystoreConfig.getKeystores().get(ftpsServerConfig.getTrustStore());
        ssl.setClientAuthentication("true");
        ssl.setTruststoreFile(new File(truststoreInfo.getFilePath()));
        ssl.setTruststorePassword(truststoreInfo.getFilePassword());

        SslConfiguration sslConfiguration = ssl.createSslConfiguration();
        DataConnectionConfigurationFactory dcConfFactory = new DataConnectionConfigurationFactory();

        dcConfFactory.setPassiveExternalAddress(commonConfig.getPublicAddress());
        dcConfFactory.setSslConfiguration(sslConfiguration);
        dcConfFactory.setPassivePorts(ftpsServerConfig.getDataportrange());
        dcConfFactory.setImplicitSsl(false);

        factory.setDataConnectionConfiguration(dcConfFactory.createDataConnectionConfiguration());
        factory.setSslConfiguration(sslConfiguration);
        factory.setImplicitSsl(false);
    
        // replace the default listener
        serverFactory.addListener("default", factory.createListener());

        // Usermanager
        serverFactory.setUserManager(new CertUserManager(commonConfig.getFtpHomeDirs(),denyListService,ftpsServerConfig,peerConfig));

        // start the server
        server = serverFactory.createServer();
        try {       
            server.start();
        } catch (FtpException e) {
            e.printStackTrace(System.out); 
        }
    }    
}
