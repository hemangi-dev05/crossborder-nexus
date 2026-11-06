
package com.fintech.controltower.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintech.controltower.domain.PaymentRequest;
import com.fintech.controltower.service.PaymentSagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://127.0.0.1:3000"
})
public class PaymentIngestionController {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentIngestionController.class);

    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile("[A-Za-z]{3}");

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;
    private final ObjectMapper objectMapper;

    public PaymentIngestionController(
            PaymentSagaOrchestrator paymentSagaOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.paymentSagaOrchestrator = paymentSagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = "/initiate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> initiatePayment(
            @RequestBody PaymentRequest request
    ) {

        String validationError = validate(request);

        if (validationError != null) {
            log.warn("Rejected payment initiation: {}", validationError);

            return jsonResponse(
                    HttpStatus.BAD_REQUEST,
                    "REJECTED",
                    validationError
            );
        }

        try {
            String uetr =
                    paymentSagaOrchestrator.executePaymentSaga(request);

            String sagaOutcome = jsonResponseString(
                    "ACCEPTED",
                    "Payment accepted for simulated processing. UETR: " + uetr
            );

            return ResponseEntity
                    .status(resolveHttpStatus(sagaOutcome))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(sagaOutcome);

        } catch (SecurityException e) {

            log.warn(
                    "Payment blocked by compliance policy: {}",
                    e.getMessage()
            );

            return jsonResponse(
                    HttpStatus.FORBIDDEN,
                    "SUSPENDED_AML_HOLD",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            log.warn("Invalid payment details: {}", e.getMessage());

            return jsonResponse(
                    HttpStatus.BAD_REQUEST,
                    "REJECTED",
                    e.getMessage()
            );

        } catch (Exception e) {

            log.error(
                    "Unhandled pipeline failure during ingestion execution",
                    e
            );

            return jsonResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FAILED",
                    "An unexpected payment processing error occurred"
            );
        }
    }

    private String validate(PaymentRequest request) {

        if (request == null) {
            return "Request body is required";
        }

        BigDecimal amount = request.amount();

        if (amount == null) {
            return "Transaction amount is required";
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Transaction amount must be greater than zero";
        }

        if (request.currency() == null
                || !CURRENCY_PATTERN.matcher(
                request.currency().trim()
        ).matches()) {

            return "Currency must contain exactly three letters";
        }

        return null;
    }

    private HttpStatus resolveHttpStatus(String sagaOutcomeJson) {

        try {
            JsonNode status =
                    objectMapper.readTree(sagaOutcomeJson).path("status");

            return switch (status.asText("")) {
                case "ACCEPTED" -> HttpStatus.ACCEPTED;
                case "SUSPENDED_AML_HOLD" -> HttpStatus.FORBIDDEN;
                case "REJECTED" -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };

        } catch (Exception e) {

            log.error("Unable to parse saga outcome payload", e);

            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String jsonResponseString(
            String status,
            String message
    ) {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("status", status);
        body.put("message", message);

        return body.toString();
    }

    private ResponseEntity<String> jsonResponse(
            HttpStatus httpStatus,
            String status,
            String message
    ) {

        return ResponseEntity
                .status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResponseString(status, message));
    }
}
