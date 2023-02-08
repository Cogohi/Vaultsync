package org.avlis.vaultsync.security;

import java.util.Objects;
import java.util.stream.Stream;

import org.avlis.vaultsync.config.PeerConfig;
import org.avlis.vaultsync.config.ReceiverConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private DenyListService denyListService;
    private ReceiverConfig receiverConfig;
    private PeerConfig peerConfig;

    @Autowired
    public SecurityConfig(  DenyListService denyListService,
                            PeerConfig peerConfig,
                            ReceiverConfig receiverConfig)
    {
        this.denyListService = denyListService;
        this.peerConfig = peerConfig;
        this.receiverConfig = receiverConfig;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .requiresChannel(channel -> 
                channel.anyRequest().requiresSecure())
            .authorizeHttpRequests()
                .requestMatchers("/v1/transfer/**").access(hasIpAddress(receiverConfig.getSubnet()))
                .requestMatchers("/v1/sync/**").hasAuthority("ROLE_SYNC")
            .and()
                .x509()
                    .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                    .userDetailsService(userDetailsService());
        http.csrf().disable();
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                Objects.requireNonNull(username);
                if(denyListService.isCnBlocked(username)) {
                    log.error("Blocked sender '{}'",username);
                    throw new UsernameNotFoundException("You don't belong here");
                }
                // FIXME: Need an X509Configurer that will check both the primary and subject alternative names.
                if(!peerConfig.isKnownPeer(username)) {
                    log.error("Unknown sender '{}'",username);
                    throw new UsernameNotFoundException("I don't know you");
                }
                return new User(username,"",
                    AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_SYNC"));
            }
        };
    }

    private static AuthorizationManager<RequestAuthorizationContext> hasIpAddress(String ipAddress) {
        if(ipAddress.equalsIgnoreCase("disabled")) {
            return  (authentication, context) -> {
                return new AuthorizationDecision(true);
            };
        }

        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            boolean matched = Stream.of(ipAddress.split(",", -1))
                .map(String::trim)
                .anyMatch(mask -> new IpAddressMatcher(mask).matches(request));
            log.debug("Does {} match subnet {}? {}",request.getRemoteAddr(),ipAddress,matched);
            return new AuthorizationDecision(matched);
        };
    }

}
