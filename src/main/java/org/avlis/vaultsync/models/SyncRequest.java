package org.avlis.vaultsync.models;

import lombok.*;

@Getter
@Setter
public class SyncRequest extends SyncData {

    // set on receive
    private String sender;
    private long receivedMillis; // currenttimemilis
    private int statusCode;

    public SyncRequest(SyncData syncData, String sender) {
        super();
        
        this.characterName = syncData.characterName;
        this.loginName = syncData.loginName;
        this.cdkey = syncData.cdkey;
        this.fileName = syncData.fileName;
        this.fileSize = syncData.fileSize;
        this.checksum = syncData.checksum;

        this.sender = sender;
        this.statusCode = 102;  // Processing. "request in flight"
        this.receivedMillis = System.currentTimeMillis();        
    }
}
