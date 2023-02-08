package org.avlis.vaultsync.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.avlis.vaultsync.models.*;
import org.avlis.vaultsync.services.SyncService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReceiverController {

    private final SyncService syncService;

    @Autowired
    public ReceiverController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping(value = "/v1/sync/start", consumes = "application/json", produces = "application/json")
    public StartResults start(@RequestBody SyncData data, HttpServletRequest request, HttpServletResponse response) {
        return this.syncService.addRequest(data, request.getUserPrincipal().getName());
    }

    @PostMapping(value = "/v1/sync/verify", consumes = "application/json", produces = "application/json")
    public RequestStatus verify(@RequestBody RequestData request, HttpServletResponse response) {

        return this.syncService.verifyTransfer(request.getRequestId());
    }

    @PostMapping(value = "/v1/sync/cancel", consumes = "application/json", produces = "application/json")
    public RequestStatus cancel(@RequestBody RequestData request, HttpServletResponse response) {
        return this.syncService.cancelTransfer(request.getRequestId());
    }
}
