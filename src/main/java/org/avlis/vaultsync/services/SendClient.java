package org.avlis.vaultsync.services;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.UUID;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.KeyManagerUtils;
import org.apache.commons.net.util.TrustManagerUtils;
import org.avlis.vaultsync.config.CommonConfig;
import org.avlis.vaultsync.config.FTPSClientConfig;
import org.avlis.vaultsync.config.KeystoreConfig;
import org.avlis.vaultsync.config.KeystoreInfo;
import org.avlis.vaultsync.config.PeerInfo;
import org.avlis.vaultsync.config.SenderConfig;
import org.avlis.vaultsync.models.*;
import org.avlis.vaultsync.util.ChecksumUtil;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendClient {

    private ChecksumUtil checksumUtil;
    private RestTemplateBuilder restTemplateBuilder;

    private KeystoreInfo trustStoreInfo;
    private KeystoreInfo keyStoreInfo;

    private FTPSClient ftpsClient;

    private boolean logRestExceptions;
    private boolean logFtpsException;

    private boolean busy = false;
    private boolean aborted = false;

    // DO NOT AUTOWIRE - This is managed by the FTPSClientPoolFactory
    public SendClient(  FTPSClientConfig ftpClientConfig,
                        CommonConfig commonConfig,
                        SenderConfig senderConfig,
                        ChecksumUtil checksumUtil,
                        KeystoreConfig keystoreConfig,
                        RestTemplateBuilder restTemplateBuilder)
    {
        this.checksumUtil = checksumUtil;
        this.restTemplateBuilder = restTemplateBuilder;

        trustStoreInfo = keystoreConfig.getKeystores().get(ftpClientConfig.getTrustStore());
        keyStoreInfo = keystoreConfig.getKeystores().get(ftpClientConfig.getKeyStore());

        logRestExceptions = commonConfig.isLogAllExceptions() || senderConfig.isLogExceptions();
        logFtpsException = commonConfig.isLogAllExceptions() || ftpClientConfig.isLogExceptions();

        ftpsClient = makeFTPSClient();
    }

    private FTPSClient makeFTPSClient() {
        FTPSClient ftpsClient = new FTPSClient();

        try {
            KeyStore truststore = KeyStore.getInstance(new File(trustStoreInfo.getFilePath()), trustStoreInfo.getFilePassword().toCharArray());
            TrustManager trustManager = TrustManagerUtils.getDefaultTrustManager(truststore);
            ftpsClient.setTrustManager(trustManager);
        } catch (IOException | GeneralSecurityException e) {
            log.error("Problem making truststore: {}",e.getMessage());
            if(logFtpsException) {
                log.debug("truststore exception: ",e);
            }
        }

        try {
            KeyManager keyManager = KeyManagerUtils.createClientKeyManager("JKS",
                new File(keyStoreInfo.getFilePath()),
                keyStoreInfo.getFilePassword(),
                keyStoreInfo.getKeyAlias(),
                keyStoreInfo.getKeyPassword());
            ftpsClient.setKeyManager(keyManager);
        } catch (IOException | GeneralSecurityException e) {
            log.error("Problem making keystore: {}",e.getMessage());
            if(logFtpsException) {
                log.debug("keystore exception: ",e);
            }
        }

        return ftpsClient;
    }

    public boolean isBusy() {
        return busy;
    }

    public void abort() {
        aborted = true;
    }

    public void startPeer(File sourceFile, PeerInfo peer, TransferRequest xferRequest) {
        busy = true;
        aborted = false;

        TransferStatus xferStatus = xferRequest.getStatus();
        String baseUrl = "https://"+peer.getHost()+":"+peer.getRestPort();

        // call peer /v1/sync/start
        SyncData syncData = new SyncData();
        syncData.setCharacterName(xferRequest.getCharacterName());
        syncData.setLoginName(xferRequest.getLoginName());
        syncData.setCdkey(xferRequest.getCdkey());
        syncData.setFileName(xferRequest.getFileName());
        syncData.setFileSize(sourceFile.length());
        syncData.setChecksum(checksumUtil.getChecksum(sourceFile.toPath()));

        StartResults startResults;
        log.debug("Before {}/v1/sync/start",baseUrl);
        try {
            startResults = callRestService(baseUrl, HttpMethod.POST, "/v1/sync/start", syncData, StartResults.class);
        } catch(RestClientResponseException e) {
            log.info("call to /v1/sync/start failed: see the restTemplate.exchange() error");
            startResults = new StartResults(null, 500, "Problem with transfer. Please contact the staff");
        }
        log.debug("After /v1/sync/start");

        if(startResults.getStatusCode() > 299) {
            xferStatus.setStatusCode(startResults.getStatusCode());
            xferStatus.setErrorMessage(startResults.getErrorMessage());
            log.error("Peer rejected transfer for {} / {} / {} : {}",
                xferRequest.getCdkey(),
                xferRequest.getLoginName(),
                xferRequest.getCharacterName(),
                startResults.getErrorMessage()
            );
            busy = false;
            return;
        }

        String syncId = startResults.getRequestId().toString();
        RequestData requestData = new RequestData();
        requestData.setRequestId(UUID.fromString(syncId));

        log.debug("Before FTPS");
        send(sourceFile, syncId, peer, xferStatus);
        log.debug("After FTPS");

        int errorCode = xferStatus.getStatusCode();
        // aborted
        if(errorCode > 299) {
            // call peer /v1/sync/cancel
            RequestStatus requestStatus;
            try {
                requestStatus = callRestService(baseUrl, HttpMethod.POST, "/v1/sync/cancel", requestData, RequestStatus.class);
            } catch(RestClientResponseException e) {
                log.info("call to /v1/sync/cancel failed: see the restTemplate.exchange() error");
                requestStatus = new RequestStatus(500, "Problem with transfer. Please contact the staff");
            }
            if(requestStatus.getStatusCode() > 299) {
                xferStatus.setStatusCode(requestStatus.getStatusCode());
                xferStatus.setErrorMessage(requestStatus.getErrorMessage());
                log.error("Transfer for syncId {} cancel failed: {}",syncId,requestStatus.getErrorMessage());
            } else {
                log.info("Transfer for syncId {} {}",syncId,errorCode == 410 ? "aborted" : "failed");
            }
        } else
        // success!
        if(errorCode < 201) {
            // calls peer /v1/sync/validate
            RequestStatus requestStatus;
            try {
                requestStatus = callRestService(baseUrl, HttpMethod.POST, "/v1/sync/verify", requestData, RequestStatus.class);
            } catch(RestClientResponseException e) {
                log.info("call to /v1/sync/verify failed: see the restTemplate.exchange() error");
                requestStatus = new RequestStatus(500, "Problem with transfer. Please contact the staff");
            }
            if(requestStatus.getStatusCode() > 299) {
                xferStatus.setStatusCode(requestStatus.getStatusCode());
                xferStatus.setErrorMessage(requestStatus.getErrorMessage());
                log.error("Transfer for syncId {} failed validation: {}",syncId,requestStatus.getErrorMessage());
            } else {
                log.info("Transfer for syncId {} complete",syncId);
                xferStatus.setStatusCode(200);
            }
        }

        busy = false;
    }

    private <S,T> T callRestService(String baseUrl, HttpMethod method, String uri, S body, Class<T> clazz)
    {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setAcceptCharset(Collections.singletonList(StandardCharsets.UTF_8));
        HttpEntity<S> request = new HttpEntity<>(body,headers);
        ResponseEntity<T> response;
        try {
            response = restTemplate.exchange(baseUrl+"/"+uri, method, request, clazz);
        } catch(Exception ex) {
            log.error("restTemplate.exchange() exception: {}",ex.getMessage());
            if(logRestExceptions) {
                log.debug("CallRestService() exception: ",ex);
            }
            throw new RestClientResponseException(
                "Issue between server and destination, please contact the staff",
                HttpStatusCode.valueOf(500),
                "Internal Server Error",
                null,
                null,
                null);
        }
        if(response.getStatusCode().isError()) {
            String statusCodeString = response.getStatusCode().toString();
            log.error("restTemplate.exchange() REST call failed with: {}",statusCodeString);
            throw new RestClientResponseException(
                "REST call failed",
                response.getStatusCode(),
                statusCodeString,
                response.getHeaders(),
                null,
                null);
        }
        return response.getBody();
    }

    public void send(File sourceFile, String syncId, PeerInfo peer, TransferStatus xferStatus) {

        OutputStream destinationOS = null;

        try {
            log.debug("FTPS client connecting to {}:{}",peer.getHost(),peer.getFtpsPort());
            ftpsClient.connect(peer.getHost(),peer.getFtpsPort());

            // dummy username and password, the certificate is the authenticator
            if(ftpsClient.login("plugh","cretin")) {
                ftpsClient.execPBSZ(0);
                ftpsClient.execPROT("P");
                ftpsClient.enterLocalPassiveMode();
                ftpsClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
                ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);

                // destination name will be the UUID stored in syncId
                destinationOS = ftpsClient.storeFileStream(syncId);
            }

            if(destinationOS == null) {
                log.error("Peer's FTPServer rejected the connection for syncId: "+syncId);
                xferStatus.setStatusCode(500);
                xferStatus.setErrorMessage("There was an error with the transfer, please contact the staff");
                return;
            }

            FileInputStream sourceIS = new FileInputStream(sourceFile);

            long length = sourceFile.length();
            int read = 0;
            long transferred = 0;
            byte[] bytesIn = new byte[4096];

            while (!aborted && (read = sourceIS.read(bytesIn)) != -1) {
                destinationOS.write(bytesIn, 0, read);
                transferred += read;
                // progress is percentage.  It's scaled by 1000 to give one decimal of precision
                xferStatus.setProgress((int)((transferred*1000)/length));
            }
            sourceIS.close();
            destinationOS.flush();
            destinationOS.close();

            if(aborted) {
                log.warn("Transfer for syncId "+syncId+" aborted");
                ftpsClient.abort();
                xferStatus.setStatusCode(410);
                xferStatus.setErrorMessage("Transfer aborted");
            } else {
                if( ftpsClient.completePendingCommand()) {
                    // success!
                } else {
                    // ruh-roh... is there a way to recover? or get the error?
                    log.error("Transfer for syncId "+syncId+" did not finalize");
                    xferStatus.setStatusCode(500);
                    xferStatus.setErrorMessage("All bytes transferred but library failed to finalize");
                }
            }

        } catch(Exception ex) {
            log.error("Transfer for syncId {} failed: {}", syncId, ex.getMessage());
            if(logFtpsException) {
                log.debug("FTPS exception: ",ex);
            }
            xferStatus.setStatusCode(500);
            xferStatus.setErrorMessage(ex.getMessage());
        } finally {
            log.debug("Finally called");
            try {
                if (ftpsClient.isConnected()) {
                    ftpsClient.logout();
                    ftpsClient.disconnect();
                }
            } catch(IOException ex) {
                log.error("Exception thrown during finally after transfer for syncId {}",syncId,ex);
            }
        }

    }
}
