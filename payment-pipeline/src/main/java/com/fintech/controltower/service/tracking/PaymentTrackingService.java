
package com.fintech.controltower.service.tracking;

import com.fintech.controltower.api.dto.PaymentDetailsResponse;
import com.fintech.controltower.api.dto.PaymentPageResponse;
import com.fintech.controltower.api.dto.PaymentHistoryResponse;
import com.fintech.controltower.api.dto.IsoMessageResponse;
import com.fintech.controltower.api.dto.NetworkSubmissionResponse;
import com.fintech.controltower.api.dto.PaymentTrackingResponse;

import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;
import com.fintech.controltower.domain.entity.PaymentStatusHistory;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.entity.IsoMessage;
import com.fintech.controltower.domain.repository.IsoMessageRepository;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentTrackingService {

    private final TransactionLedgerRepository ledgerRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final IsoMessageRepository isoMessageRepository;
    private final NetworkSubmissionRepository networkSubmissionRepository;

    public PaymentTrackingService(
            TransactionLedgerRepository ledgerRepository,
            PaymentStatusHistoryRepository historyRepository,
            IsoMessageRepository isoMessageRepository,
            NetworkSubmissionRepository networkSubmissionRepository) {

        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
        this.isoMessageRepository = isoMessageRepository;
        this.networkSubmissionRepository = networkSubmissionRepository;
    }

    // Retrieve one payment using UETR
    public PaymentDetailsResponse getPaymentByUetr(String uetr) {

        TransactionLedger payment = ledgerRepository.findById(uetr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found for UETR: " + uetr
                ));

        return mapToResponse(payment);
    }

    // Retrieve complete payment status history using UETR
    public List<PaymentHistoryResponse> getPaymentHistory(String uetr) {

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalArgumentException("UETR cannot be empty.");
        }

        ledgerRepository.findById(uetr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found for UETR: " + uetr
                ));

        List<PaymentStatusHistory> history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        return history.stream()
                .map(PaymentHistoryResponse::from)
                .toList();
    }

    // Retrieve persisted ISO messages using UETR
    @Transactional(readOnly = true)
    public List<IsoMessageResponse> getIsoMessages(String uetr) {

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalArgumentException("UETR cannot be empty.");
        }

        ledgerRepository.findById(uetr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found for UETR: " + uetr
                ));

        List<IsoMessage> messages =
                isoMessageRepository
                        .findByTransactionLedger_UetrOrderByCreatedAtAsc(uetr);

        return messages.stream()
                .map(this::mapToIsoMessageResponse)
                .toList();
    }

    // Retrieve complete payment tracking information using UETR
    @Transactional(readOnly = true)
    public PaymentTrackingResponse getPaymentTracking(String uetr) {

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalArgumentException("UETR cannot be empty.");
        }

        // 1. Retrieve the payment
        TransactionLedger payment = ledgerRepository.findById(uetr)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found for UETR: " + uetr
                ));

        // 2. Retrieve network submission evidence
        List<NetworkSubmissionResponse> networkSubmissions =
                networkSubmissionRepository
                        .findByTransactionLedger_UetrOrderBySubmittedAtAsc(uetr)
                        .stream()
                        .map(submission ->
                                NetworkSubmissionResponse.from(submission, uetr))
                        .toList();

        // 3. Retrieve ISO 20022 messages
        List<IsoMessageResponse> isoMessages =
                isoMessageRepository
                        .findByTransactionLedger_UetrOrderByCreatedAtAsc(uetr)
                        .stream()
                        .map(this::mapToIsoMessageResponse)
                        .toList();

        // 4. Retrieve complete status history
        List<PaymentHistoryResponse> statusHistory =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr)
                        .stream()
                        .map(PaymentHistoryResponse::from)
                        .toList();

        // Combine all information into one response
        return new PaymentTrackingResponse(
                mapToResponse(payment),
                networkSubmissions,
                isoMessages,
                statusHistory
        );
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

    // Convert ISO message entity into response DTO
    private IsoMessageResponse mapToIsoMessageResponse(
            IsoMessage message) {

        return new IsoMessageResponse(
                message.getId(),
                message.getTransactionLedger().getUetr(),
                message.getMessageType(),
                message.getSchemaVersion(),
                message.getValidationStatus(),
                message.getCreatedAt(),
                message.getMessageContent()
        );
    }
}
