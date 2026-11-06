package com.fintech.controltower.api.controller;

import com.fintech.controltower.api.dto.PaymentStatusResponse;
import com.fintech.controltower.api.dto.StatusUpdateRequest;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.service.TransactionStatusService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulated-network/payments")
public class SimulatedNetworkController {

    private final TransactionStatusService statusService;

    public SimulatedNetworkController(
            TransactionStatusService statusService) {
        this.statusService = statusService;
    }

    @PatchMapping("/{uetr}/status")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable String uetr,
            @RequestBody StatusUpdateRequest request) {

        try {
            TransactionLedger updated =
                    statusService.updateStatus(uetr, request.status());

            PaymentStatusResponse response = new PaymentStatusResponse(
                    updated.getUetr(),
                    updated.getTransactionId(),
                    updated.getStatus(),
                    updated.getUpdatedAt()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(exception.getMessage());

        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(exception.getMessage());
        }
    }
}