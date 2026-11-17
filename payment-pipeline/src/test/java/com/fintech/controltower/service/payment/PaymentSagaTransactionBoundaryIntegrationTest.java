
package com.fintech.controltower.service.payment;

import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;
import com.fintech.controltower.financial.model.AmountMode;
import com.fintech.controltower.service.network.DomesticClearingAdapter;
import com.fintech.controltower.service.network.NetworkSubmissionResult;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class PaymentSagaTransactionBoundaryIntegrationTest {

    @Autowired
    private PaymentSagaOrchestrator paymentSagaOrchestrator;

    @Autowired
    private TransactionLedgerRepository ledgerRepository;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Autowired
    private NetworkSubmissionRepository submissionRepository;

    @SpyBean
    private DomesticClearingAdapter domesticClearingAdapter;

    @Test
    void shouldCommitInitialPaymentBeforeSubmittingOutsideTransaction() {

        String uetr = UUID.randomUUID().toString();

        PaymentRequest request = new PaymentRequest(
                "TX-TEST-" + UUID.randomUUID(),
                "Test Sender",
                "IN0000000001",
                "TESTINBBXXX",
                "IN",
                null,
                null,
                "Test Receiver",
                "IN0000000002",
                "TESTINBBXXX",
                "IN",
                null,
                null,
                "INR",
                "INR",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        AtomicBoolean transactionBoundaryVerified =
                new AtomicBoolean(false);

        // Inspect the database transaction state at network submission.
        doAnswer(invocation -> {

            // The network adapter must execute outside a DB transaction.
            assertThat(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            ).isFalse();

            // The initial payment must already be committed and visible.
            TransactionLedger persistedLedger =
                    ledgerRepository.findById(uetr).orElseThrow();

            assertThat(persistedLedger.getStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.ACCEPTED
                    );

            var initialHistory =
                    historyRepository.findByUetrOrderByChangedAtAsc(uetr);

            assertThat(initialHistory).hasSize(1);

            assertThat(initialHistory.get(0).getNewStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.ACCEPTED
                    );

            transactionBoundaryVerified.set(true);

            LocalDateTime now = LocalDateTime.now();

            return new NetworkSubmissionResult(
                    "DOMESTIC_CLEARING",
                    "TEST-NET-" + UUID.randomUUID(),
                    NetworkSubmissionResult.SubmissionStatus.SIMULATED_ACKNOWLEDGED,
                    now,
                    now,
                    "Test network acknowledged payment",
                    null
            );

        }).when(domesticClearingAdapter).submit(
                any(PaymentRequest.class),
                any(),
                eq(uetr)
        );

        // Execute the actual payment orchestrator.
        PaymentSagaResult result =
                paymentSagaOrchestrator.executePaymentSaga(
                        request,
                        uetr
                );

        // Verify that the adapter observed the expected transaction boundary.
        assertThat(transactionBoundaryVerified.get()).isTrue();

        assertThat(result.outcome())
                .isEqualTo(PaymentSagaResult.Outcome.ACKNOWLEDGED);

        // Verify the final payment status.
        TransactionLedger finalLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(finalLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        // Verify that network submission evidence was persisted.
        var submissions =
                submissionRepository
                        .findByTransactionLedger_UetrOrderBySubmittedAtAsc(
                                uetr
                        );

        assertThat(submissions).hasSize(1);

        // Important: NetworkSubmission has its own SubmissionStatus enum.
        assertThat(submissions.get(0).getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_ACKNOWLEDGED
                );

        // Verify both lifecycle history entries.
        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(2);

        assertThat(history.get(0).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.ACCEPTED
                );

        assertThat(history.get(1).getPreviousStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.ACCEPTED
                );

        assertThat(history.get(1).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );
    }
}
