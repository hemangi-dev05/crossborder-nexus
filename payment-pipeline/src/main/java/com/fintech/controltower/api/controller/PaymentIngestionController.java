
package com.fintech.controltower.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.exception.IdempotencyConflictException;
import com.fintech.controltower.financial.model.AmountMode;
import com.fintech.controltower.service.idempotency.PaymentIdempotencyService;
import com.fintech.controltower.service.idempotency.PaymentIdempotencyService.PaymentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        },
        allowedHeaders = {
                "Content-Type",
                "Idempotency-Key"
        }
)
public class PaymentIngestionController {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentIngestionController.class);

    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile("[A-Za-z]{3}");

    private final PaymentIdempotencyService paymentIdempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentIngestionController(
            PaymentIdempotencyService paymentIdempotencyService,
            ObjectMapper objectMapper
    ) {
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = "/initiate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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

            PaymentResult result =
                    paymentIdempotencyService.processPayment(
                            idempotencyKey,
                            request
                    );

            HttpStatus httpStatus = switch (result.state()) {

                case ACCEPTED -> result.replayed()
                        ? HttpStatus.OK
                        : HttpStatus.ACCEPTED;

                case NETWORK_PENDING -> HttpStatus.ACCEPTED;

                case AML_HOLD -> HttpStatus.FORBIDDEN;

                case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;

                case IN_PROGRESS -> HttpStatus.CONFLICT;

                case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            };

            return jsonResponse(
                    httpStatus,
                    result.state().name(),
                    result.message()
            );

        } catch (IdempotencyConflictException e) {

            log.warn("Idempotency conflict: {}", e.getMessage());

            return jsonResponse(
                    HttpStatus.CONFLICT,
                    "CONFLICT",
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

        if (request.enteredAmount() == null) {
            return "Entered amount is required";
        }

        if (request.enteredAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Entered amount must be greater than zero";
        }

        if (!isValidCurrency(request.sourceCurrency())) {
            return "Source currency must contain exactly three letters";
        }

        if (!isValidCurrency(request.destinationCurrency())) {
            return "Destination currency must contain exactly three letters";
        }

        if (request.amountMode() == null) {
            return "Amount mode must be SOURCE_AMOUNT or RECEIVER_AMOUNT";
        }

        return null;
    }

    private boolean isValidCurrency(String currency) {

        return currency != null
                && CURRENCY_PATTERN.matcher(
                currency.trim()
        ).matches()
                && isSupportedCurrency(currency);
    }

    private boolean isSupportedCurrency(String currency) {

        try {
            java.util.Currency.getInstance(
                    currency.trim().toUpperCase(Locale.ROOT)
            );
            return true;

        } catch (IllegalArgumentException exception) {
            return false;
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
