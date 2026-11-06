
package com.fintech.controltower.service;

import com.fintech.controltower.api.dto.PaymentDetailsResponse;
import com.fintech.controltower.api.dto.PaymentPageResponse;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentTrackingService {

    private final TransactionLedgerRepository ledgerRepository;

    public PaymentTrackingService(TransactionLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    // Retrieve one payment using UETR
    public PaymentDetailsResponse getPaymentByUetr(String uetr) {

        TransactionLedger payment = ledgerRepository.findById(uetr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found for UETR: " + uetr
                ));

        return mapToResponse(payment);
    }

    // Retrieve all payments or filter by status
    public PaymentPageResponse getAllPayments(
            int page,
            int size,
            TransactionLedger.TransactionStatus status) {

        Pageable pageable = createPageable(page, size);

        Page<TransactionLedger> result;

        if (status == null) {
            result = ledgerRepository.findAll(pageable);
        } else {
            result = ledgerRepository.findByStatus(status, pageable);
        }

        return mapToPageResponse(result);
    }

    // Search payments by UETR or transaction ID
    public PaymentPageResponse searchPayments(
            String query,
            int page,
            int size) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        String searchTerm = query.trim();
        Pageable pageable = createPageable(page, size);

        Page<TransactionLedger> result =
                ledgerRepository.findByUetrOrTransactionId(
                        searchTerm,
                        searchTerm,
                        pageable
                );

        return mapToPageResponse(result);
    }

    // Validate and create pagination
    private Pageable createPageable(int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100."
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    // Convert database page into API response
    private PaymentPageResponse mapToPageResponse(
            Page<TransactionLedger> result) {

        List<PaymentDetailsResponse> payments = result.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return new PaymentPageResponse(
                payments,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    // Convert entity into safe response DTO
    private PaymentDetailsResponse mapToResponse(
            TransactionLedger payment) {

        return new PaymentDetailsResponse(
                payment.getUetr(),
                payment.getTransactionId(),
                payment.getDebtorName(),
                payment.getCreditorName(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
