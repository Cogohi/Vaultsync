package org.avlis.vaultsync.controllers;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.avlis.vaultsync.models.*;
import org.avlis.vaultsync.services.SenderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class TransferController {

    private final SenderService senderService;

    @Autowired
    public TransferController(SenderService syncService) {
        this.senderService = syncService;
    }

    @PostMapping(value = "/v1/transfer/start", consumes = "application/json", produces = "application/json")
    public RequestStatus start(@RequestBody TransferData data, HttpServletResponse response) {
        return this.senderService.addRequest(data);
    }

    @PostMapping(value = "/v1/transfer/status", consumes = "application/json", produces = "application/json")
    public TransferStatus status(@RequestBody TransferKey request, HttpServletResponse response) {
        String charId = request.getCdkey()+":"+request.getCharacterName();
        log.info("Checking status for "+charId+" destination: "+request.getDestination());
        return this.senderService.getStatus(charId,request.getDestination());
    }

    @PostMapping(value = "/v1/transfer/abort", consumes = "application/json", produces = "application/json")
    public RequestStatus cancel(@RequestBody TransferKey request, HttpServletResponse response) {
        String charId = request.getCdkey()+":"+request.getCharacterName();
        return this.senderService.abortTransfer(charId,request.getDestination());
    }
}
