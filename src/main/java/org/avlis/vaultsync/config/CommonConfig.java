package org.avlis.vaultsync.config;

import org.avlis.vaultsync.util.PathValidationUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties("common")
public class CommonConfig implements Validator {
    // which senders we've blocked
    @Valid
    private String cnDenyList;
    // where the received files wind up
    @Valid
    private String ftpHomeDirs;
    // where to find the servervault
    @Valid
    private String vaultPath;
    @NotBlank
    private String publicAddress;
    private boolean logAllExceptions = false;

    @Override
    public boolean supports(Class<?> clazz) {
        return CommonConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        CommonConfig commonConfig = (CommonConfig) target;

        // May be blank
        String cnDenyList = commonConfig.getCnDenyList();
        if(cnDenyList != null) {
            PathValidationUtils.checkFilePath("cnDenyList",cnDenyList,errors);
        }

        PathValidationUtils.checkDirectoryPath("ftpHomeDirs",commonConfig.getFtpHomeDirs(),errors);
        PathValidationUtils.checkDirectoryPath("vaultPath",commonConfig.getVaultPath(),errors);
    }

}
