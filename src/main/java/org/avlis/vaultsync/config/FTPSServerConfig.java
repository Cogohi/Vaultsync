package org.avlis.vaultsync.config;

import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

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
@ConfigurationProperties("ftps.server")
public class FTPSServerConfig implements Validator {
    @Autowired
    private KeystoreConfig keystoreConfig;

    @NotNull
    private Integer port;
    @Valid
    private String keyStore;
    @Valid
    private String trustStore;
    @Min(0)
    private Integer maxloginnumber = 0;
    @Min(0)
    private Integer maxloginperip = 0;
    @Min(0)
    private Integer uploadrate = 0;
    @Pattern(regexp = "^\\d{1,5}-\\d{1,5}$")
    private String dataportrange = "30000-30010";
    private boolean logExceptions = false;

	@Override
	public boolean supports(Class<?> clazz) {
		return FTPSServerConfig.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
        FTPSServerConfig ftpsServerConfig = (FTPSServerConfig) target;

        keystoreConfig.validateKeyStore("keystore",ftpsServerConfig.getKeyStore(),"keyStore",errors);
        keystoreConfig.validateKeyStore("truststore",ftpsServerConfig.getTrustStore(), "trustStore", errors);
	}
}
