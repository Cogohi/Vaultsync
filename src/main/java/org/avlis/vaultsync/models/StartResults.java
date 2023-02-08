package org.avlis.vaultsync.models;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StartResults {
    private UUID requestId;
    private int statusCode;
    private String errorMessage;
}
