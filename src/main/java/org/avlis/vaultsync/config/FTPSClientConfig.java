package org.avlis.vaultsync.config;

import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

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
@ConfigurationProperties("ftps.client")
public class FTPSClientConfig implements Validator {
    @Autowired
    KeystoreConfig keystoreConfig;

    @NotNull
    private Integer minPoolSize;
    @NotNull
    private Integer maxPoolSize;
    @Valid
    private String keyStore;
    @Valid
    private String trustStore;
    private boolean logExceptions = false;

	@Override
	public boolean supports(Class<?> clazz) {
		return FTPSClientConfig.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
        FTPSClientConfig ftpsClientConfig = (FTPSClientConfig) target;

        keystoreConfig.validateKeyStore("keystore",ftpsClientConfig.getKeyStore(),"keyStore",errors);
        keystoreConfig.validateKeyStore("truststore",ftpsClientConfig.getTrustStore(), "trustStore", errors);
	}
}
