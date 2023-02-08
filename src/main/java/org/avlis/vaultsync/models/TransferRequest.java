package org.avlis.vaultsync.models;

import lombok.*;

@Getter
@Setter
public class TransferRequest extends TransferData {

    private TransferStatus status;      // model instance returned from getStatus()
    private long receivedMillis;    // used for expiration

    public TransferRequest(TransferData xferData) {
        super();

        this.characterName = xferData.characterName;
        this.loginName = xferData.loginName;
        this.cdkey = xferData.cdkey;
        this.fileName = xferData.fileName;
        this.destination = xferData.destination;

        this.status = new TransferStatus(0, -1, "");
        this.receivedMillis = System.currentTimeMillis();
    }
}
