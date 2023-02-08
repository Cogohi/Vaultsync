package org.avlis.vaultsync.services;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.avlis.vaultsync.config.*;
import org.avlis.vaultsync.models.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;

@Slf4j
@Component
public class SenderService {

    private PeerConfig peerConfig;
    private SendClientManager sendClientManager;
    
    boolean vaultByCdkey;
    String vaultPath;

    private Map<String, TransferRequest> transfers;

    @Autowired
    SenderService(  CommonConfig directoryConfig,
                    PeerConfig peerConfig,
                    ReceiverConfig receiverConfig,
                    SendClientManager sendClientManager)
    {
        this.peerConfig = peerConfig;
        this.sendClientManager = sendClientManager;
        this.vaultByCdkey = receiverConfig.getVaultByCdkey();
        this.vaultPath = directoryConfig.getVaultPath();

        ExpirationListener<String,TransferRequest> l = (key,req) -> {
            log.info("Expiring transfer request with key: "+key);
        };

        transfers = ExpiringMap.builder()
            .expiration(receiverConfig.getSyncTimeout(), TimeUnit.SECONDS)
            .expirationListener(l)
            .build();
    }

    public RequestStatus addRequest(TransferData requestData) {
        String cdkey = requestData.getCdkey();
        String login = requestData.getLoginName();
        String charName = requestData.getCharacterName();
        String filename = requestData.getFileName();
        String destination = requestData.getDestination();
        String charId = cdkey+":"+charName;

        // if cdkey is in flight and hasn't expired return transfer in progress error
        if(hasInflightTransfer(charId)) {
            log.info("Pending transfer exists for CDKEY: "+cdkey+" charName: "+charName);
            return new RequestStatus( 401, "Pending transfer for "+charName+" exists");
        }

        // Validate file exists, is a file, and is readable
        Path filePath = Paths.get(vaultPath, vaultByCdkey?cdkey:login, filename+".bic");
        String message = "transfer request to "+destination+" for cdkey: "+cdkey+" login: "+login+" character: "+charName;

        if(Files.notExists(filePath)) {
            log.error("Could not schedule "+message+" reason: cannot find "+filePath);
            return new RequestStatus(404, "Could not transfer, please contact the staff");
        }
        if(!Files.isRegularFile(filePath)) {
            log.error("Could not schedule "+message+" reason: "+filePath+" is not a regular file");
            return new RequestStatus(500, "Could not transfer, please contact the staff");
        }
        if(!Files.isReadable(filePath)) {
            log.error("Could not schedule "+message+" reason: no read permission for "+filePath);
            return new RequestStatus(500, "Could not transfer, please contact the staff");
        }

        // Validate peer exists and is enabled
        PeerInfo peerInfo = peerConfig.getPeers().get(destination);
        if(peerInfo == null || !peerInfo.getEnabled()) {
            log.error("Bad peer request "+destination+" "+(peerInfo==null?"is not defined in application.yml":"is not enabled"));
            return new RequestStatus( 404, destination+" is not a valid destination, please contact the staff");
        }

        TransferRequest request = new TransferRequest(requestData);

        transfers.put(charId,request);

        sendClientManager.scheduleTransfer(charId, peerInfo, filePath, request);

        log.info("Scheduled transfer request to "+destination+" for cdkey: "+cdkey+" login: "+login+" character: "+charName);

        return new RequestStatus(-1, null);
    }

    public TransferStatus getStatus(String charId, String destination) {
        if(!transfers.containsKey(charId)) {
            return new TransferStatus(0, 404, "Transfer request not found.");
        }

        TransferRequest request = transfers.get(charId);
        String transferDestination = request.getDestination();
        if(!transferDestination.equals(destination)) {
            log.error("Rejected status request: supplied: '{}' transfer's: '{}'", destination, transferDestination);
            return new TransferStatus(0, 403, "Destination mismatch.");
        }

        TransferStatus status = request.getStatus();
        if(status.getStatusCode() == 200) {
            log.info("Send request "+charId+" has completed");
        } else
        if(status.getStatusCode() > 299) {
            log.error("Send request "+charId+" reported an error: "+status.getErrorMessage());
        } else {
            log.error("Send request "+charId+" pending: "+status.getErrorMessage());
        }

        return status;
    }

    public RequestStatus abortTransfer(String charId, String destination) {
        if(!transfers.containsKey(charId)) {
            return new RequestStatus(404, "Transfer request not found.");
        }

        TransferRequest request = transfers.get(charId);
        String transferDestination = request.getDestination();
        if(!transferDestination.equals(destination)) {
            log.error("Rejected abort request: supplied: '{}' transfer's: '{}'", destination, transferDestination);
            return new RequestStatus(403, "Destination mismatch.");
        }

        transfers.remove(charId);

        TransferStatus status = request.getStatus();
        if(status.getStatusCode() != 200) {
            sendClientManager.abort(charId);
        }

        log.info("Request "+charId+" cancelled");

        return new RequestStatus(200, "");
    }

    private boolean hasInflightTransfer(String charId) {
        TransferRequest transferRequest = transfers.get(charId);
        if(transferRequest == null)
            return false;

        return transferRequest.getStatus().getStatusCode() < 200;
    }
}
