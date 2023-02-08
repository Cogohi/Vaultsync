package org.avlis.vaultsync.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
public class PeerInfo {
    @NotBlank
    String host;
    // optional
    String primaryName = null;
    @NotNull
    Integer restPort;
    @NotNull
    Integer ftpsPort;
    @NotNull
    Boolean enabled;
}
