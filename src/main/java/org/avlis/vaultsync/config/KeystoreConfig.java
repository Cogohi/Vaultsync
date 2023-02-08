package org.avlis.vaultsync.config;

import lombok.*;

import java.util.*;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties
public class KeystoreConfig {
    @NotNull
    Map<String,KeystoreInfo> keystores;

    public void validateKeyStore(String type, String infoKey, String field, Errors errors) {
        if(infoKey == null) {
            errors.rejectValue(field, "must.not.be.empty", "must not be empty");
            return;
        }
        KeystoreInfo info = keystores.get(infoKey);
        if(info == null) {
            errors.rejectValue(field, "not.found", "Selected "+type+" does not exist in keystores");
        } else
        if(!type.equalsIgnoreCase(info.getType())) {
            errors.rejectValue(field, "bad.type", "Selected keystore entry is not a "+type);
        }
    }
}
