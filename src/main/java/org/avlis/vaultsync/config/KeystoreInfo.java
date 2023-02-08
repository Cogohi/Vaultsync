package org.avlis.vaultsync.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import org.avlis.vaultsync.util.PathValidationUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
public class KeystoreInfo implements Validator {
    @Pattern(regexp = "keystore|truststore", flags = Pattern.Flag.CASE_INSENSITIVE)
    String type;    // current valid options 'keystore' or 'truststore'
    @Valid
    String filePath;
    @NotBlank
    String filePassword;
    // this can be blank
    String keyPassword = "";
    @Valid
    String keyAlias;

    @Override
    public boolean supports(Class<?> clazz) {
        return KeystoreInfo.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        KeystoreInfo keystoreInfo = (KeystoreInfo) target;

        PathValidationUtils.checkFilePath("filePath", keystoreInfo.getFilePath(), errors);

        if("keystore".equalsIgnoreCase(keystoreInfo.getType())) {
            String keyAlias = keystoreInfo.getKeyPassword();
            if(keyAlias == null || keyAlias.isBlank()) {
                errors.rejectValue("keyAlias","must.not.be.blank","Alias is required");
            }
        }
    }
}
