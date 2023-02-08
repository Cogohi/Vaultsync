package org.avlis.vaultsync.services;

import java.io.IOException;

import java.nio.file.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;

import org.avlis.vaultsync.config.*;
import org.avlis.vaultsync.models.*;
import org.avlis.vaultsync.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SyncService {

    private CommonConfig commonConfig;
    private ReceiverConfig receiverConfig;
    private ChecksumUtil checksumUtil;
    private NamedParameterJdbcTemplate jdbcTemplate;

    // NOTE: probably need a thread to periodically clean these out
    private Map<UUID, SyncRequest> transfers;
    private Map<String, UUID> uuidByCharId = new HashMap<>();

    @Autowired
    public SyncService( CommonConfig commonConfig,
                        ReceiverConfig receiverConfig,
                        ChecksumUtil checksumUtil,
                        NamedParameterJdbcTemplate jdbcTemplate)
    {
        this.commonConfig = commonConfig;
        this.receiverConfig = receiverConfig;
        this.checksumUtil = checksumUtil;
        this.jdbcTemplate = jdbcTemplate;

        // when the transfer request is evicted also evict the reverse lookup
        ExpirationListener<UUID, SyncRequest> l = (key,req) -> {
            cleanupTransfer(key, req);
        };

        // evict transfers after duration
        transfers = ExpiringMap.builder()
            .expiration(receiverConfig.getSyncTimeout(), TimeUnit.SECONDS)
            .expirationListener(l)
            .build();
    }

    /**
     * Verifies that the sever can accept the character and registers the transfer details
     * @param string
     * @param request The details of the transfer
     * @return The UUID assigned to the transfer or error details
     */
    public StartResults addRequest(SyncData requestData, String sender) {
        //  5. peer VSS checks size and runs DB queries to verify if the portal is allowed
        //      if cdkey is in flight and hasn't expired return transfer in progress error
        //      if size > maxsize then return too big error
        //      if server status query comes back blocked then return blocked error
        //      if login or cdkey status query come back banned or jailed then return banned or jailed error
        //      if character status query comes back wrong world then return wrong world error
        //  a. peer creates new syncId and associates it with the SyncData object
        //  6. peer VSS responds OK with sync_id

        if(!checkSenderFtpHome(sender)) {
            // checkSenderFtpHome will log error deatils
            return new StartResults(null, 500, "Error on the destination server.  Please contact their staff");
        }

        String cdkey = requestData.getCdkey();
        String login = requestData.getLoginName();
        String charName = requestData.getCharacterName();
        String charId = cdkey+":"+charName;

        // if cdkey is in flight and hasn't expired return transfer in progress error
        if(hasInflightTransfer(charId)) {
            log.info("Pending transfer exists for sender: "+sender+" CDKEY: "+cdkey+" login: "+login);
            return new StartResults(null, 401, "Pending transfer for CDKEY exists");
        }

        // if size > maxsize then return too big error
        long maxsize = receiverConfig.getMaxsize();
        long filesize = requestData.getFileSize();
        if(maxsize > -1 && maxsize < filesize) {
            log.info("Requested bic size "+filesize+" exceeds our max limit of "+maxsize+" for login: "+login+" character: "+charName+" from: "+sender);
            return new StartResults(null, 413, "File too large");
        }

        SyncRequest request = new SyncRequest(requestData, sender);

        StartResults rejection = checkValidations(request);
        if(rejection != null) {
            log.warn("Validation failed for transfer request from: "+sender+" for cdkey: "+cdkey+" login: "+login+" character: "+charName+" reason: "+rejection.getErrorMessage());
            return rejection;
        }

        UUID syncId = UUID.randomUUID();

        uuidByCharId.put(charId,syncId);
        transfers.put(syncId,request);

        log.info("Transfer request from: "+sender+" for cdkey: "+cdkey+" login: "+login+" character: "+charName+" syncId: "+syncId);

        return new StartResults(syncId, -1, null);
    }

    /**
     * Verifies that the transfer is complete, the checksum is valid, and moves the file to
     * the correct folder
     * @param requestId the UUID of the requestid
     * @return success or error details
     */
    public RequestStatus verifyTransfer(UUID requestId) {
        if(!transfers.containsKey(requestId)) {
            return makeRequestStatus(404, "Transfer request not found.",null);
        }
       
        SyncRequest request = transfers.get(requestId);

        Path senderPath = Paths.get(commonConfig.getFtpHomeDirs(), request.getSender(), requestId.toString());
        String fullPath = senderPath.toAbsolutePath().toString();
        if(Files.notExists(senderPath)) {
            log.error("Cannot verify requestId: "+requestId+" file is missing: "+fullPath);
            return makeRequestStatus(400, "File missing",request);
        }
        try {
            long sizeDiff = Files.size(senderPath) - request.getFileSize();
    
            // verify filesize
            if(sizeDiff < 0) {
                log.error("Cannot verify requestId: "+requestId+" file is incomplete: "+fullPath);
                return makeRequestStatus(400, "File incomplete",request);
            } else
            if(sizeDiff > 0) {
                log.error("Cannot verify requestId: "+requestId+" received file is too large: "+fullPath);
                return makeRequestStatus(400, "File too big",request);
            }
        } catch(IOException ex) {
            log.error("Cannot verify requestId: "+requestId+" encountered exception while handling file: "+fullPath,ex);
            return makeRequestStatus(500, "IOException: "+ex.getMessage(),request);
        }

        // verify checksum
        String checksum = checksumUtil.getChecksum(senderPath);

        if(!checksum.equals(request.getChecksum())) {
            log.error("Cannot verify requestId: "+requestId+" invalid checksum: "+fullPath);
            return makeRequestStatus(400, "Bad checksum",request);
        }

        // Get destination
        String vaultBy = receiverConfig.getVaultByCdkey() ? request.getCdkey() : request.getLoginName();
        Path vaultPath = Paths.get(commonConfig.getVaultPath(), vaultBy);
        Path destinationPath = vaultPath.resolve(request.getFileName()+".bic");

        try {
            // create destination directory if it doesn't exist
            if(Files.notExists(vaultPath)) {
                Files.createDirectory(vaultPath);
            }
    
            // move file
            Files.move(senderPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("Cannot verify requestId: "+requestId+" attempt to move file failed trying to move from "+fullPath+
                "to "+destinationPath.toAbsolutePath().toString(),e);
            return makeRequestStatus(500, "Server error, please contact destination's admins.",request);
        }

        log.info("Request "+requestId+" completed successfully");

        return makeRequestStatus(200, "", request);
    }

    /**
     * Cancels the transfer identified by the requestId
     * @param requestId the UUID of the requestid
     * @return success or error details
     */
    public RequestStatus cancelTransfer(UUID requestId) {
        SyncRequest request = transfers.get(requestId);

        if(request == null) {
            return new RequestStatus(404, "Transfer request not found.");
        }

        // remove the transfer
        transfers.remove(requestId);

        // clean up uuidByCharId and any files left
        cleanupTransfer(requestId, request);

        return new RequestStatus(200, "");
    }

    /**
     * Called <i>after</i> the element has been removed from transfers.  Used by
     * both cancelTransfer() and the ExpirationListener on transfers
     * @param requestId
     * @param request
     */
    private void cleanupTransfer(UUID requestId, SyncRequest request) {
        String charId = request.getCdkey()+":"+request.getCharacterName();
        uuidByCharId.remove(charId);

        // Remove file
        Path senderPath = Paths.get(commonConfig.getFtpHomeDirs(), request.getSender(), requestId.toString());

        try {
            Files.deleteIfExists(senderPath);
        } catch(IOException ex) {
            log.error("Cancel request "+requestId+": Could not delete "+senderPath.toAbsolutePath().toString(),ex);
        }

        if(request.getStatusCode() == 200) {
            log.info("Request "+requestId+" cleaned up");
        } else {
            log.info("Request "+requestId+" cancelled");
        }
    }

    private StartResults checkValidations(SyncRequest requestData) {
        List<ValidationQuery> queries = receiverConfig.getValidationQueries();
        // nothing to do
        if(queries == null) {
            return null;
        }
        // do the checks
        String query = "<not set>";
        try {
            for(ValidationQuery q : queries) {
                SqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("world", requestData.getSender())
                    .addValue("cdkey", requestData.getCdkey())
                    .addValue("login", requestData.getLoginName())
                    .addValue("name", requestData.getCharacterName());
                query = "SELECT count(*) "+q.getQuery();
                Integer count = jdbcTemplate.queryForObject(query, parameters, Integer.class);
                if(count != null && count > 0) {
                    return new StartResults(null, q.getRejectionCode(), q.getRejectionReason());
                }
            };
        } catch(DataAccessException ex) {
            log.error("Could not run the validation query: [{}]",query,ex);
            return new StartResults(null, 500, "Error on destination server.  Please contact their staff for assistance.");
        }
        return null;
    }

    /**
     * Verifies that the sender's FTP homedir exists and can be written to.
     * If it doesn't exist, it will try to create it.
     * @param sender
     * @return
     */
    private boolean checkSenderFtpHome(String sender) {
        Path senderPath = Paths.get(commonConfig.getFtpHomeDirs(), sender);
        if(Files.notExists(senderPath)) {
            try {
                log.info("Creating {}",senderPath);
                Files.createDirectories(senderPath);
            } catch (IOException e) {
                log.error("{} does not exist and could not be created: {}",senderPath,e.getMessage());
                return false;
            }
        } else
        if(!Files.isDirectory(senderPath)) {
            log.error("{} is not a directory",senderPath);
            return false;
        } else
        if(!Files.isWritable(senderPath)) {
            log.error("{} is not writeable",senderPath);
            return false;
        }
        return true;
    }

    private boolean hasInflightTransfer(String charId) {
        UUID xferId = uuidByCharId.get(charId);
        if(xferId == null)
            return false;

        SyncRequest request = transfers.get(xferId);
        if(request == null)
            return false;

        // is it stale?
        if(request.getStatusCode() < 200)
            return true;

        // clean up the stale transfer
        cancelTransfer(xferId);

        return false;
    }

    private RequestStatus makeRequestStatus(int statusCode, String errorMessage, SyncRequest request) {
        if(request != null)
            request.setStatusCode(statusCode);
        return new RequestStatus(statusCode, errorMessage);
    }
}
