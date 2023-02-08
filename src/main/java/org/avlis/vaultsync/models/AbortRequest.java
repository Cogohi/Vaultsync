package org.avlis.vaultsync.models;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Validated
public class AbortRequest {
    @NotEmpty
    String cdkey;
    @NotEmpty
    String characterName;
}
