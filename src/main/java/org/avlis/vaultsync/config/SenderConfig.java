package org.avlis.vaultsync.config;

import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

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
@ConfigurationProperties("sender")
public class SenderConfig implements Validator {
    @Autowired
    private KeystoreConfig keystoreConfig;

    @Min(1)
    private Integer maxInFlight = 10;
    @Valid
    private String keyStore;
    @Valid
    private String trustStore;
    @NotEmpty
    private String sslProtocol = "TLSv1.3";
    @Min(0)
    private Integer transferTimeout = 600;
    @Min(0)
    private Long connectTimeout = 60L;
    @Min(0)
    private Long connectionRequestTimeout = 60L;
    private boolean logExceptions = false;

    @Override
    public boolean supports(Class<?> clazz) {
        return SenderConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        SenderConfig senderConfig = (SenderConfig) target;

        keystoreConfig.validateKeyStore("keystore", senderConfig.getKeyStore(), "keyStore", errors);
        keystoreConfig.validateKeyStore("truststore", senderConfig.getTrustStore(), "trustStore", errors);
    }
}
