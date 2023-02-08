package org.avlis.vaultsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Validated
@ConfigurationProperties
public class PeerConfig {
    @NotNull
    private @Setter @Getter Map<String,PeerInfo> peers;

    private Set<String> knownPeerNames = null;

    public boolean isKnownPeer(String username) {
        if(knownPeerNames == null) {
            buildKnownPeers();
        }
        return knownPeerNames.contains(username);
    }

    private void buildKnownPeers() {
        knownPeerNames = peers.values().stream()
            // only care about enabled peers
            .filter(PeerInfo::getEnabled)
            // extract the host and primary name
            .map(peerinfo -> {
                List<String> names = new ArrayList<>();
                names.add(peerinfo.getHost());
                String primary = peerinfo.getPrimaryName();
                if(primary != null) {
                    names.add(primary);
                }
                return names;
            })
            // collapse the stream of lists of strings to a stream of strings
            .flatMap(Collection::stream)
            // turn it into a Set
            .collect(Collectors.toSet());
    }

    /**
     * Prevent Spring from stomping on knownPeerNames
     */
    public void setKnownPeerNames(Set<String> knownPeerNames) {
        log.error("knownPeerNames is not a valid configuration property");
    }
}
