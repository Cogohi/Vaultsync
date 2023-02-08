package org.avlis.vaultsync.services;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import org.apache.commons.pool2.impl.GenericObjectPool;

import org.avlis.vaultsync.config.PeerInfo;
import org.avlis.vaultsync.config.SenderConfig;
import org.avlis.vaultsync.models.TransferRequest;
import org.avlis.vaultsync.util.SendClientPoolFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SendClientManager {

    ThreadPoolExecutor executor;
    GenericObjectPool<SendClient> objectPool;

    Map<String,SendClient> clientMap = Collections.synchronizedMap(new HashMap<>());

    @Autowired
    public SendClientManager(   SenderConfig config,
                                SendClientPoolFactory factory)
    {
        int maxInFlight = config.getMaxInFlight();

        // ThreadPool with up to 10 threads and an unbounded queue
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxInFlight);
        executor.setKeepAliveTime(2000, TimeUnit.MILLISECONDS);
        executor.allowsCoreThreadTimeOut();

        // SendClient ObjectPool
        objectPool = new GenericObjectPool<>(factory);
        objectPool.setMaxTotal(maxInFlight);
    }

    public void scheduleTransfer(String charId, PeerInfo peerInfo, Path filePath, TransferRequest request) {

        Runnable task = () -> {
            SendClient sendClient = null;

            // get a SendClient instance
            try {
                sendClient = objectPool.borrowObject();
            } catch (Exception e) {
                log.error("Transfer "+charId+" could not be processed due to an ObjectPool exception:",e);
                return;
            }

            // track the client just in case we need to abort the transfer
            synchronized(clientMap) {
                clientMap.put(charId, sendClient);
            }

            // run the peer /start -> FTPS -> /cancel or /verify lifecycle
            sendClient.startPeer(filePath.toFile(), peerInfo, request);

            // untrack the client
            synchronized(clientMap) {
                clientMap.remove(charId);
            }

            // return the SendClient instance
            objectPool.returnObject(sendClient);
        };

        // queue the task for execution
        executor.submit(task);
    }

    public void abort(String charId) {

        SendClient sendClient;
        synchronized(clientMap) {
            sendClient = clientMap.get(charId);
        }

        if(sendClient == null) {
            log.error("SendClient for Transfer "+charId+" is not in the ClientMap.  Could not cancel the transfer.");
        } else {
            // This is just a signal.  The SendClient will halt the transfer
            // and coordinate with the peer to cancel the transfer request.
            sendClient.abort();
        }
    }

}
