package org.avlis.vaultsync.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.avlis.vaultsync.config.CommonConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DenyListService {

    private Set<String> cnDenyList;

    @Autowired
    public DenyListService(CommonConfig commonConfig) {
        cnDenyList = populateDenyListFromFile(commonConfig.getCnDenyList());
        log.debug("CD deny list has {} entries",cnDenyList.size());
    }

    public boolean isCnBlocked(String name) {
        return cnDenyList.contains(name);
    }

    private Set<String> populateDenyListFromFile(String filename) {
        if(filename != null) {
            // one entry per line
            Path path = Paths.get(filename);
            BufferedReader reader;
            try {
                reader = Files.newBufferedReader(path);
                return reader.lines()
                    .map(DenyListService::cleanEntry)
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.toSet());
            } catch (IOException e) {
                log.warn("IOException trying to read denylist from {}",filename,e);
            }
        }

        return new HashSet<>();
    }

    private static String cleanEntry(String line) {
        // find trailing comment
        int index = line.indexOf("#");
        // remove comment and spaces before and after
        if(index > -1) {
            line = line.substring(0, index);
        }
        return line.trim();
    }
}
