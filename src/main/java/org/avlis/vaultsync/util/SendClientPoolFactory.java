package org.avlis.vaultsync.util;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.avlis.vaultsync.config.CommonConfig;
import org.avlis.vaultsync.config.FTPSClientConfig;
import org.avlis.vaultsync.config.KeystoreConfig;
import org.avlis.vaultsync.config.SenderConfig;
import org.avlis.vaultsync.services.SendClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.*;

@Service
public class SendClientPoolFactory implements PooledObjectFactory<SendClient> {

    private FTPSClientConfig ftpsClientConfig;
    private CommonConfig commonConfig;
    private SenderConfig senderConfig;
    private ChecksumUtil checksumUtil;
    private KeystoreConfig keystoreConfig;
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    public SendClientPoolFactory(   FTPSClientConfig ftpsClientConfig,
                                    CommonConfig commonConfig,
                                    SenderConfig senderConfig,
                                    ChecksumUtil checksumUtil,
                                    KeystoreConfig keystoreConfig,
                                    RestTemplateBuilder restTemplateBuilder)
    {
        this.ftpsClientConfig = ftpsClientConfig;
        this.commonConfig = commonConfig;
        this.senderConfig = senderConfig;
        this.checksumUtil = checksumUtil;
        this.keystoreConfig = keystoreConfig;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Override
    public void activateObject(PooledObject<SendClient> p) throws Exception {
    }

    @Override
    public void destroyObject(PooledObject<SendClient> p) throws Exception {
        SendClient client = p.getObject();
        client.abort();
    }

    @Override
    public PooledObject<SendClient> makeObject() throws Exception {
        return new DefaultPooledObject<SendClient>(new SendClient(ftpsClientConfig,commonConfig,senderConfig,checksumUtil,keystoreConfig,restTemplateBuilder));
    }

    @Override
    public void passivateObject(PooledObject<SendClient> p) throws Exception {
    }

    @Override
    public boolean validateObject(PooledObject<SendClient> p) {
        SendClient client = p.getObject();
        return client.isBusy();
    }
}
