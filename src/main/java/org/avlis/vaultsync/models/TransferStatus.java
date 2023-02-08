package org.avlis.vaultsync.models;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class TransferStatus {
    private int progress; // 0-1000
    private int statusCode;
    private String errorMessage;
}
