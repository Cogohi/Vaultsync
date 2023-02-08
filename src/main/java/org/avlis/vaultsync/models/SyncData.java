package org.avlis.vaultsync.models;

import lombok.Data;

@Data
public class SyncData {
    protected String characterName;
    protected String loginName;
    protected String cdkey;
    protected String fileName;
    protected long fileSize;
    protected String checksum;
}
