
package com.fintech.controltower.api.controller;

import com.fintech.controltower.api.dto.PaymentDetailsResponse;
import com.fintech.controltower.api.dto.PaymentPageResponse;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.service.PaymentTrackingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentTrackingController {

    private final PaymentTrackingService trackingService;

    public PaymentTrackingController(PaymentTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // Retrieve a payment using UETR
    @GetMapping("/{uetr}")
    public ResponseEntity<?> getPayment(@PathVariable String uetr) {

        try {
            PaymentDetailsResponse response =
                    trackingService.getPaymentByUetr(uetr);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(exception.getMessage());
        }
    }

    // Retrieve all payments or filter by status
    @GetMapping
    public ResponseEntity<?> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)
            TransactionLedger.TransactionStatus status) {

        try {
            PaymentPageResponse response =
                    trackingService.getAllPayments(page, size, status);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(exception.getMessage());
        }
    }

    // Search payments by UETR or transaction ID
    @GetMapping("/search")
    public ResponseEntity<?> searchPayments(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            PaymentPageResponse response =
                    trackingService.searchPayments(query, page, size);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(exception.getMessage());
        }
    }
}
