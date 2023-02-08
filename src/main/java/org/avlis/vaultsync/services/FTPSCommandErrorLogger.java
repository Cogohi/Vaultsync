package org.avlis.vaultsync.services;

import java.io.IOException;
import java.util.*;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FTPSCommandErrorLogger extends DefaultFtplet {

    Map<UUID,String> sessionUsers = new HashMap<>();

    @Override
    public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply)
            throws FtpException, IOException
    {
        UUID sessionUuid = session.getSessionId();

        if(request.getCommand().equalsIgnoreCase("USER")) {
            String username = request.getArgument();
            log.debug("Got USER {} for session {}",username,sessionUuid);
            sessionUsers.put(sessionUuid, username);
        }

        int code = reply.getCode();
        String name = sessionUsers.getOrDefault(sessionUuid, "<not set>");

        if(code == 530) {
            log.error("FTPS received a login attempt from [{}] but failed to authenticate.",name);
        } else
        if(code > 399) {
            log.error("FTPS error {} {} user: [{}]",code,reply.getMessage(),name);
        }
        return super.afterCommand(session, request, reply);
    }

    @Override
    public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
        UUID sessionUuid = session.getSessionId();
        sessionUsers.remove(sessionUuid);
        return super.onDisconnect(session);
    }
}
