package org.avlis.vaultsync.models;

import lombok.Data;

@Data
public class TransferData {
    // NOTE: TransferRequest is a subclass and needs access
    protected String characterName;
    protected String loginName;
    protected String cdkey;
    protected String fileName;
    protected String destination;
}
