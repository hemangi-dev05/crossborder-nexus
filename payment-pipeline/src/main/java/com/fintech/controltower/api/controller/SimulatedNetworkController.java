
package com.fintech.controltower.api.controller;

import com.fintech.controltower.api.dto.DelayedNetworkResponseRequest;
import com.fintech.controltower.api.dto.NetworkResponseApiResponse;
import com.fintech.controltower.api.dto.PaymentStatusResponse;
import com.fintech.controltower.api.dto.StatusUpdateRequest;
import com.fintech.controltower.domain.entity.NetworkResponse;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.service.network.NetworkResponseService;
import com.fintech.controltower.service.payment.TransactionStatusService;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulated-network/payments")
public class SimulatedNetworkController {

    private final TransactionStatusService statusService;
    private final NetworkResponseService networkResponseService;

    public SimulatedNetworkController(
            TransactionStatusService statusService,
            NetworkResponseService networkResponseService
    ) {
        this.statusService = statusService;
        this.networkResponseService = networkResponseService;
    }

    // Existing endpoint: manually update payment status
    @PatchMapping("/{uetr}/status")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable String uetr,
            @RequestBody StatusUpdateRequest request
    ) {

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

    // New endpoint: receive delayed network ACK/NACK
    @PostMapping("/submissions/{submissionId}/response")
    public ResponseEntity<?> receiveDelayedNetworkResponse(
            @PathVariable Long submissionId,
            @RequestBody DelayedNetworkResponseRequest request
    ) {

        try {
            NetworkResponse response =
                    networkResponseService.processDelayedResponse(
                            submissionId,
                            request.status(),
                            request.responseMessage()
                    );

            NetworkResponseApiResponse apiResponse =
                    NetworkResponseApiResponse.from(
                            response,
                            submissionId
                    );

            return ResponseEntity.ok(apiResponse);

        } catch (EntityNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(exception.getMessage());

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(exception.getMessage());

        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(exception.getMessage());
        }
    }
}
