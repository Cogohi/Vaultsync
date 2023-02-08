package org.avlis.vaultsync.models;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Validated
public class TransferKey {
    @NotEmpty
    private String cdkey;
    @NotEmpty
    private String characterName;
    @NotEmpty
    private String destination;
}
