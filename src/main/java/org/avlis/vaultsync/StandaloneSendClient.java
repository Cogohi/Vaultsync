package org.avlis.vaultsync;

import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.KeyManagerUtils;
import org.apache.commons.net.util.TrustManagerUtils;
import org.avlis.vaultsync.config.KeystoreInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StandaloneSendClient {
	public static void main(String[] args) {
        new StandaloneSendClient().doSend();
    }

    public void doSend() {
        FTPSClient ftpsClient;

        KeystoreInfo keystoreInfo = new KeystoreInfo();
        keystoreInfo.setFilePath("./config/otherkeys.jks");
        keystoreInfo.setFilePassword("otherkey");
        keystoreInfo.setKeyPassword("otherkey");
        keystoreInfo.setKeyAlias("other");
        KeystoreInfo truststoreInfo = new KeystoreInfo();
        truststoreInfo.setFilePath("./config/trust-store.jks");
        truststoreInfo.setFilePassword("changeit");

        ftpsClient = makeFTPSClient(keystoreInfo, truststoreInfo);

        try {
            ftpsClient.connect("localhost",2220);
            ftpsClient.login("admin","admin");
            ftpsClient.execPBSZ(0);
            ftpsClient.execPROT("P");
            ftpsClient.enterLocalPassiveMode();
            ftpsClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
    
            File testFile = new File("test.txt");
            InputStream testIS = new FileInputStream(testFile);
            OutputStream testOS = ftpsClient.storeFileStream("target.txt");
    
            System.out.println("Uploading file");

            int read = 0;
            byte[] bytesIn = new byte[4096];
    
            while ((read = testIS.read(bytesIn)) != -1) {
                System.out.println("chunk read");
                testOS.write(bytesIn, 0, read);
                System.out.println("chunk written");
            }
            System.out.println("Closing input");
            testIS.close();
            System.out.println("Input closed");
            testOS.flush();
            testOS.close();
            System.out.println("Output closed");
    
            if( ftpsClient.completePendingCommand()) {
                System.out.println("Upload success");
            }

            // if( ftpsClient.storeFile("target.txt", testIS) ) {
            //     System.out.println("Upload success");
            // }

            System.out.println("Done");
        } catch(IOException ex) {
            System.out.println("Error: "+ex.getMessage());
            ex.printStackTrace();
        } finally {
            System.out.println("Cleaning up");
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private FTPSClient makeFTPSClient(KeystoreInfo keyStoreInfo, KeystoreInfo trustStoreInfo) {
        FTPSClient ftpsClient = new FTPSClient();

        try {
            KeyStore truststore = KeyStore.getInstance(new File(trustStoreInfo.getFilePath()), trustStoreInfo.getFilePassword().toCharArray());
            TrustManager trustManager = TrustManagerUtils.getDefaultTrustManager(truststore);
            ftpsClient.setTrustManager(trustManager);
        } catch (IOException | GeneralSecurityException e) {
            log.error("Problem making truststore",e);
        }

        try {
            KeyManager keyManager = KeyManagerUtils.createClientKeyManager("JKS",
                new File(keyStoreInfo.getFilePath()),
                keyStoreInfo.getFilePassword(),
                keyStoreInfo.getKeyAlias(),
                keyStoreInfo.getKeyPassword());
            ftpsClient.setKeyManager(keyManager);
        } catch (IOException | GeneralSecurityException e) {
            log.error("Problem making keystore",e);
        }

        return ftpsClient;
    }

}
