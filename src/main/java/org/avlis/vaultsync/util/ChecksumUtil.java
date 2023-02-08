package org.avlis.vaultsync.util;

import java.io.InputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.nio.file.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

@Component
public class ChecksumUtil {
    private MessageDigest digest;
    
    public ChecksumUtil() throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance("SHA-512");
    }

    /**
     * Returns the SHA-512 checksum of the file
     */
    public String getChecksum(Path path) {
        int count = 0;
        byte[] buffer = new byte[4096];
        
        digest.reset();

        try {
            InputStream is = Files.newInputStream(path, StandardOpenOption.READ);
            while((count = is.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
    
            byte[] hash = digest.digest();
            BigInteger bHash = new BigInteger(1, hash);
            return String.format("%0" + (hash.length << 1) + "x", bHash);
        } catch(IOException ex) {
            ex.printStackTrace();
        }

        return "ERROR";
    }
}
