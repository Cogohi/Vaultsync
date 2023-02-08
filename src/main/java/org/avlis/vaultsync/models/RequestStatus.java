package org.avlis.vaultsync.models;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class RequestStatus {
    private int statusCode;
    private String errorMessage;
}
