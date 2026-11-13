
package com.fintech.controltower.service;

import com.fintech.controltower.domain.NetworkResponse;
import com.fintech.controltower.domain.NetworkResponseRepository;
import com.fintech.controltower.domain.NetworkSubmission;
import com.fintech.controltower.domain.NetworkSubmissionRepository;
import com.fintech.controltower.domain.PaymentStatusHistory;
import com.fintech.controltower.domain.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NetworkResponseIntegrationTest {

    @Autowired
    private NetworkResponseService networkResponseService;

    @Autowired
    private TransactionLedgerRepository ledgerRepository;

    @Autowired
    private NetworkSubmissionRepository submissionRepository;

    @Autowired
    private NetworkResponseRepository responseRepository;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Test
    void shouldPersistDelayedAcknowledgementAndUpdatePaymentStatus() {

        // 1. Create a payment waiting for a network response
        String uetr = UUID.randomUUID().toString();

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId("INTEGRATION-TEST-001");
        ledger.setAmount(new BigDecimal("2500.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(
                TransactionLedger.TransactionStatus.NETWORK_PENDING
        );

        ledgerRepository.saveAndFlush(ledger);

        // 2. Create the original timed-out network submission
        NetworkSubmission submission = new NetworkSubmission(
                ledger,
                "CROSS_BORDER_CORRESPONDENT",
                "SIM-TEST-" + UUID.randomUUID(),
                NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT,
                LocalDateTime.now().minusMinutes(5),
                null,
                "Original network request timed out"
        );

        submission = submissionRepository.saveAndFlush(submission);

        Long submissionId = submission.getId();

        // 3. Process a delayed ACK through the real service
        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        submissionId,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        "Delayed ACK received from correspondent bank"
                );

        // 4. Verify that the response was persisted
        assertThat(response.getId()).isNotNull();

        NetworkResponse storedResponse =
                responseRepository
                        .findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getStatus())
                .isEqualTo(
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED
                );

        // 5. Verify that the payment status was updated in PostgreSQL
        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.PROCESSING);

        // 6. Verify that exactly one status history record was created
        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);

        PaymentStatusHistory historyEntry = history.get(0);

        assertThat(historyEntry.getPreviousStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.NETWORK_PENDING);

        assertThat(historyEntry.getNewStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.PROCESSING);

        // 7. Verify that the original timeout submission remains unchanged
        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);

        assertThat(originalSubmission.getRespondedAt()).isNull();
    }


    @Test
    void shouldPersistDelayedRejectionAndUpdatePaymentStatus() {

        String uetr = UUID.randomUUID().toString();

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId("INTEGRATION-NACK-" + UUID.randomUUID());
        ledger.setAmount(new BigDecimal("1800.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(TransactionLedger.TransactionStatus.NETWORK_PENDING);

        ledgerRepository.saveAndFlush(ledger);

        NetworkSubmission submission = new NetworkSubmission(
                ledger,
                "CROSS_BORDER_CORRESPONDENT",
                "SIM-TEST-" + UUID.randomUUID(),
                NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT,
                LocalDateTime.now().minusMinutes(5),
                null,
                "Original network request timed out"
        );

        submission = submissionRepository.saveAndFlush(submission);
        Long submissionId = submission.getId();

        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        submissionId,
                        NetworkResponse.ResponseStatus.SIMULATED_NACK,
                        "Payment rejected by correspondent bank"
                );

        assertThat(response.getId()).isNotNull();

        NetworkResponse storedResponse =
                responseRepository.findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getStatus())
                .isEqualTo(NetworkResponse.ResponseStatus.SIMULATED_NACK);

        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.REJECTED);

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.NETWORK_PENDING);
        assertThat(history.get(0).getNewStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.REJECTED);

        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);
        assertThat(originalSubmission.getRespondedAt()).isNull();
    }



    @Test
    void shouldHandleDuplicateDelayedAcknowledgementWithoutDuplicateRecords() {

        String uetr = UUID.randomUUID().toString();
        String responseMessage =
                "Delayed ACK received from correspondent bank";

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId("INTEGRATION-DUPLICATE-" + UUID.randomUUID());
        ledger.setAmount(new BigDecimal("3000.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(TransactionLedger.TransactionStatus.NETWORK_PENDING);

        ledgerRepository.saveAndFlush(ledger);

        NetworkSubmission submission = new NetworkSubmission(
                ledger,
                "CROSS_BORDER_CORRESPONDENT",
                "SIM-TEST-" + UUID.randomUUID(),
                NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT,
                LocalDateTime.now().minusMinutes(5),
                null,
                "Original network request timed out"
        );

        submission = submissionRepository.saveAndFlush(submission);
        Long submissionId = submission.getId();

        NetworkResponse firstResponse =
                networkResponseService.processDelayedResponse(
                        submissionId,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        responseMessage
                );

        NetworkResponse duplicateResponse =
                networkResponseService.processDelayedResponse(
                        submissionId,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        responseMessage
                );

        assertThat(firstResponse.getId()).isNotNull();
        assertThat(duplicateResponse.getId())
                .isEqualTo(firstResponse.getId());

        NetworkResponse storedResponse =
                responseRepository.findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getId()).isEqualTo(firstResponse.getId());

        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.PROCESSING);

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.NETWORK_PENDING);
        assertThat(history.get(0).getNewStatus())
                .isEqualTo(TransactionLedger.TransactionStatus.PROCESSING);

        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);
        assertThat(originalSubmission.getRespondedAt()).isNull();
    }

}
