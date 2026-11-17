
package com.fintech.controltower.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintech.controltower.api.dto.PaymentInitiationRequest;
import com.fintech.controltower.config.PaymentOriginProperties;
import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;
import com.fintech.controltower.exception.IdempotencyConflictException;
import com.fintech.controltower.service.idempotency.PaymentIdempotencyService;
import com.fintech.controltower.service.idempotency.PaymentIdempotencyService.PaymentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
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

    private static final Pattern COUNTRY_PATTERN =
            Pattern.compile("[A-Za-z]{2}");

    private final PaymentIdempotencyService paymentIdempotencyService;
    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;
    private final PaymentOriginProperties originProperties;
    private final ObjectMapper objectMapper;

    public PaymentIngestionController(
            PaymentIdempotencyService paymentIdempotencyService,
            CustomerRepository customerRepository,
            CustomerAccountRepository accountRepository,
            PaymentOriginProperties originProperties,
            ObjectMapper objectMapper
    ) {
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.originProperties = originProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = "/initiate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentInitiationRequest request,
            Authentication authentication
    ) {

        String validationError = validate(request);

        if (validationError != null) {
            return jsonResponse(
                    HttpStatus.BAD_REQUEST,
                    "REJECTED",
                    validationError
            );
        }

        try {
            String email = authentication.getName()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            Customer customer = customerRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException(
                            "Authenticated customer was not found"
                    ));

            CustomerAccount account = accountRepository
                    .findByIdAndCustomer_Id(
                            request.accountId(),
                            customer.getId()
                    )
                    .orElseThrow(AccountNotFoundException::new);

            if (account.getStatus()
                    != CustomerAccount.AccountStatus.ACTIVE) {
                return jsonResponse(
                        HttpStatus.CONFLICT,
                        "REJECTED",
                        "Selected account is not active"
                );
            }

            PaymentRequest paymentRequest = buildPaymentRequest(
                    request,
                    account
            );

            // Customer-scoped idempotency:
            // the same key can be used independently by different customers.
            PaymentResult result =
                    paymentIdempotencyService.processPayment(
                            customer.getId(),
                            idempotencyKey,
                            paymentRequest
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

        } catch (AccountNotFoundException e) {
            return jsonResponse(
                    HttpStatus.NOT_FOUND,
                    "REJECTED",
                    "Selected account was not found for this customer"
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

    private PaymentRequest buildPaymentRequest(
            PaymentInitiationRequest request,
            CustomerAccount account
    ) {

        return new PaymentRequest(
                UUID.randomUUID().toString().replace("-", ""),

                account.getAccountHolderName(),
                account.getAccountNumber(),
                originProperties.getBic(),
                originProperties.getCountryCode(),
                null,
                null,

                request.creditorName().trim(),
                request.creditorAccountNumber().trim(),
                normalize(request.creditorBic()),
                normalize(request.creditorCountryCode()),
                null,
                null,

                account.getCurrency(),
                normalize(request.destinationCurrency()),
                request.amountMode(),
                request.enteredAmount()
        );
    }

    private String validate(PaymentInitiationRequest request) {

        if (request == null) {
            return "Request body is required";
        }

        if (request.accountId() == null) {
            return "A sender account must be selected";
        }

        if (isBlank(request.creditorName())) {
            return "Beneficiary name is required";
        }

        if (isBlank(request.creditorAccountNumber())) {
            return "Beneficiary account number is required";
        }

        if (!isValidCountry(request.creditorCountryCode())) {
            return "Beneficiary country must contain exactly two letters";
        }

        if (request.enteredAmount() == null
                || request.enteredAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Entered amount must be greater than zero";
        }

        if (!isValidCurrency(request.destinationCurrency())) {
            return "Destination currency must be a supported three-letter code";
        }

        if (request.amountMode() == null) {
            return "Amount mode must be SOURCE_AMOUNT or RECEIVER_AMOUNT";
        }

        return null;
    }

    private boolean isValidCurrency(String currency) {

        if (currency == null
                || !CURRENCY_PATTERN.matcher(currency.trim()).matches()) {
            return false;
        }

        try {
            java.util.Currency.getInstance(
                    currency.trim().toUpperCase(Locale.ROOT)
            );
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isValidCountry(String country) {
        return country != null
                && COUNTRY_PATTERN.matcher(country.trim()).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null
                ? null
                : value.trim().toUpperCase(Locale.ROOT);
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

    private static class AccountNotFoundException
            extends RuntimeException {
    }
}
