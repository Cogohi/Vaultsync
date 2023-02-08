package org.avlis.vaultsync.util;

import java.nio.file.*;

import org.springframework.validation.Errors;

public class PathValidationUtils {

    public static void checkFilePath(String fieldName, String pathStr, Errors errors) {
        checkPath(fieldName, pathStr, errors, false);
    }

    public static void checkDirectoryPath(String fieldName, String pathStr, Errors errors) {
        checkPath(fieldName, pathStr, errors, true);
    }

    public static void checkPath(String fieldName, String pathStr, Errors errors, boolean isDirectory) {
        if(pathStr == null || pathStr.isEmpty()) {
            errors.rejectValue(fieldName,"field.name.blank","Must be set");
        } else {
            String type = isDirectory ? "directory" : "file";
            Path path = Paths.get(pathStr);
            if(!Files.exists(path)) {
                errors.rejectValue(fieldName,"does.not.exist",new Object[]{pathStr},"No such "+type);
            } else
            if(isDirectory && !Files.isDirectory(path)) {
                errors.rejectValue(fieldName,"not.a.file",new Object[]{pathStr},"Is not a directory");
            } else
            if(!isDirectory && !Files.isRegularFile(path)) {
                errors.rejectValue(fieldName,"not.a.file",new Object[]{pathStr},"Is not a regular file");
            } else
            if(!Files.isReadable(path)) {
                errors.rejectValue(fieldName,"bad.permissions",new Object[]{pathStr},"The "+type+" is not readable");
            }
        }
    }
}
