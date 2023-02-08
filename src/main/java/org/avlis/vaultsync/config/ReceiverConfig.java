package org.avlis.vaultsync.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties("receiver")
public class ReceiverConfig implements Validator {
    @Autowired
    KeystoreConfig keystoreConfig;

    // largest .bic file this server will accept
    private Long maxsize = -1L;
    // is the servervault mapped by cdkey or login
    private Boolean vaultByCdkey = false;
    // queries (eg. blocked, banned, jailed, wrong world, etc.)
    private List<ValidationQuery> validationQueries = null;
    @Valid
    private String keyStore;
    @Valid
    private String trustStore;
    @NotNull
    @Min(1)
    @Max(65535)
    private Integer serverPort;
    @Valid
    private String subnet = "::1,127.0.0.1,172.16.0.0/12,192.168.0.0/16";
    @Min(0)
    private Integer syncTimeout = 600;
    private boolean logExceptions = false;

    @Override
    public boolean supports(Class<?> clazz) {
        return ReceiverConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ReceiverConfig receiverConfig = (ReceiverConfig) target;

        // disabled | comma separated list of subnet masks
        if(receiverConfig.getSubnet() == null) {
            errors.rejectValue("subnet", "must.not.be.blank", "subnet is required");
        }
        keystoreConfig.validateKeyStore("keystore", receiverConfig.getKeyStore(), "keyStore", errors);
        keystoreConfig.validateKeyStore("truststore", receiverConfig.getTrustStore(), "trustStore", errors);
    }
}
