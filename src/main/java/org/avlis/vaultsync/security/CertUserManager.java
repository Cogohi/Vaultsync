package org.avlis.vaultsync.security;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.UserMetadata;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.avlis.vaultsync.config.FTPSServerConfig;
import org.avlis.vaultsync.config.PeerConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CertUserManager implements UserManager {

    private String userHomePath;
    private DenyListService denyListService;
    private FTPSServerConfig ftpsServerConfig;
    private PeerConfig peerConfig;

    public CertUserManager( String userHomePath,
                            DenyListService denyListService,
                            FTPSServerConfig ftpsServerConfig,
                            PeerConfig peerConfig)
    {
        this.userHomePath = userHomePath;
        this.denyListService = denyListService;
        this.ftpsServerConfig = ftpsServerConfig;
        this.peerConfig = peerConfig;
    }

    @Override
    public User getUserByName(String username) throws FtpException {
        log.debug("getUserByName() called for {}",username);
        return null;
    }

    @Override
    public String[] getAllUserNames() throws FtpException {
        return null;
    }

    @Override
    public void delete(String username) throws FtpException {
    }

    @Override
    public void save(User user) throws FtpException {
    }

    @Override
    public boolean doesExist(String username) throws FtpException {
        log.debug("doesExist() called for {}",username);
        return false;
    }

    @Override
    public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        if(authentication instanceof UsernamePasswordAuthentication) {
            UsernamePasswordAuthentication upauth = (UsernamePasswordAuthentication) authentication;

            UserMetadata userMetadata = upauth.getUserMetadata();
            if(userMetadata == null) {
                throw new AuthenticationFailedException("Authentication failed");
            }

            Certificate[] certs = userMetadata.getCertificateChain();
            if(certs == null || certs.length == 0) {
                log.error("No certs found");
                throw new AuthenticationFailedException("Authentication failed");
            }

            if(certs[0] instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) certs[0];
                return validateX509(x509);
            } else {
                log.error("Not an X509Certificate instance");
                throw new AuthenticationFailedException("Authentication failed");
            }
        } else
        if(authentication instanceof AnonymousAuthentication) {
            AnonymousAuthentication anonauth = (AnonymousAuthentication) authentication;

            UserMetadata userMetadata = anonauth.getUserMetadata();
            if(userMetadata == null) {
                throw new AuthenticationFailedException("Authentication failed");
            }
            log.warn("Anonymous login attempt from {}", userMetadata.getInetAddress());
            throw new AuthenticationFailedException("Authentication failed");
        } else {
            throw new IllegalArgumentException("Authentication not supported by this user manager");
        }

    }

    private User validateX509(X509Certificate x509) throws AuthenticationFailedException {
        try {
            String commonName = new LdapName(x509.getSubjectX500Principal().getName()).getRdns().stream()
                .filter(i -> i.getType().equalsIgnoreCase("CN"))
                .findFirst().get().getValue().toString();
            if(denyListService.isCnBlocked(commonName)) {
                log.debug("Sender '{}' is in our deny list.",commonName);
                throw new AuthenticationFailedException("Authentication failed");
            }
            // FIXME: also validate all the subject alternative names
            if(!peerConfig.isKnownPeer(commonName)) {
                log.debug("Sender '{}' is not a known Primary common name.",commonName);
                throw new AuthenticationFailedException("Authentication failed");
            }
            return makeUser(commonName);
        } catch (InvalidNameException e) {
            log.debug("LdapName blew up:",e);
            throw new AuthenticationFailedException("Authentication failed");
        }
    }

    @Override
    public String getAdminName() throws FtpException {
        return null;
    }

    @Override
    public boolean isAdmin(String username) throws FtpException {
        return false;
    }

    private User makeUser(String username) {
        BaseUser user = new BaseUser();

        String directory = userHomePath+"/"+username;
        log.debug("Returning user {} with directory {}",username,directory);
        user.setName(username);
        user.setEnabled(true);
        user.setHomeDirectory(directory);

        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(
            ftpsServerConfig.getMaxloginnumber(),
            ftpsServerConfig.getMaxloginperip()));
        authorities.add(new TransferRatePermission(
            0,
            ftpsServerConfig.getUploadrate()));

        user.setAuthorities(authorities);
        user.setMaxIdleTime(0);

        return user;
    }
}
