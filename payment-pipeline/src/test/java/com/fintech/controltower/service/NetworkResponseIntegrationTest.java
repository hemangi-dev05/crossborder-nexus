
package com.fintech.controltower.service;

import com.fintech.controltower.domain.NetworkResponse;
import com.fintech.controltower.domain.NetworkResponseRepository;
import com.fintech.controltower.domain.NetworkSubmission;
import com.fintech.controltower.domain.NetworkSubmissionRepository;
import com.fintech.controltower.domain.PaymentStatusHistory;
import com.fintech.controltower.domain.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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

    @SpyBean
    private PaymentStatusHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;


    // --------------------------------------------------
    // TEST 1: Delayed ACK
    // --------------------------------------------------

    @Test
    void shouldPersistDelayedAcknowledgementAndUpdatePaymentStatus() {

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
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        "Delayed ACK received from correspondent bank"
                );

        assertThat(response.getId()).isNotNull();

        NetworkResponse storedResponse =
                responseRepository
                        .findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getStatus())
                .isEqualTo(
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED
                );

        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);

        PaymentStatusHistory historyEntry = history.get(0);

        assertThat(historyEntry.getPreviousStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.NETWORK_PENDING
                );

        assertThat(historyEntry.getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT
                );

        assertThat(originalSubmission.getRespondedAt()).isNull();
    }


    // --------------------------------------------------
    // TEST 2: Delayed NACK
    // --------------------------------------------------

    @Test
    void shouldPersistDelayedRejectionAndUpdatePaymentStatus() {

        String uetr = UUID.randomUUID().toString();

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId(
                "INTEGRATION-NACK-" + UUID.randomUUID()
        );
        ledger.setAmount(new BigDecimal("1800.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(
                TransactionLedger.TransactionStatus.NETWORK_PENDING
        );

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
                responseRepository
                        .findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getStatus())
                .isEqualTo(
                        NetworkResponse.ResponseStatus.SIMULATED_NACK
                );

        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.REJECTED
                );

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);

        assertThat(history.get(0).getPreviousStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.NETWORK_PENDING
                );

        assertThat(history.get(0).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.REJECTED
                );

        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT
                );

        assertThat(originalSubmission.getRespondedAt()).isNull();
    }


    // --------------------------------------------------
    // TEST 3: Sequential duplicate ACK
    // --------------------------------------------------

    @Test
    void shouldHandleDuplicateDelayedAcknowledgementWithoutDuplicateRecords() {

        String uetr = UUID.randomUUID().toString();

        String responseMessage =
                "Delayed ACK received from correspondent bank";

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId(
                "INTEGRATION-DUPLICATE-" + UUID.randomUUID()
        );
        ledger.setAmount(new BigDecimal("3000.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(
                TransactionLedger.TransactionStatus.NETWORK_PENDING
        );

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
                responseRepository
                        .findByNetworkSubmission_Id(submissionId)
                        .orElseThrow();

        assertThat(storedResponse.getId())
                .isEqualTo(firstResponse.getId());

        TransactionLedger updatedLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(uetr);

        assertThat(history).hasSize(1);

        assertThat(history.get(0).getPreviousStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.NETWORK_PENDING
                );

        assertThat(history.get(0).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        NetworkSubmission originalSubmission =
                submissionRepository.findById(submissionId).orElseThrow();

        assertThat(originalSubmission.getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT
                );

        assertThat(originalSubmission.getRespondedAt()).isNull();
    }


    // --------------------------------------------------
    // TEST 4: Concurrent identical ACK requests
    // --------------------------------------------------

    @Test
    void shouldHandleConcurrentIdenticalAcknowledgementsSafely()
            throws Exception {

        TestPayment payment =
                createPendingPayment("CONCURRENT-ACK-");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            var request =
                    (java.util.concurrent.Callable<NetworkResponse>) () -> {

                        ready.countDown();
                        start.await();

                        return networkResponseService.processDelayedResponse(
                                payment.submissionId(),
                                NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                                "Concurrent delayed ACK"
                        );
                    };

            Future<NetworkResponse> first =
                    executor.submit(request);

            Future<NetworkResponse> second =
                    executor.submit(request);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            NetworkResponse firstResponse =
                    first.get(15, TimeUnit.SECONDS);

            NetworkResponse secondResponse =
                    second.get(15, TimeUnit.SECONDS);

            assertThat(firstResponse.getId())
                    .isEqualTo(secondResponse.getId());

            NetworkResponse storedResponse =
                    responseRepository
                            .findByNetworkSubmission_Id(
                                    payment.submissionId()
                            )
                            .orElseThrow();

            assertThat(storedResponse.getId())
                    .isEqualTo(firstResponse.getId());

            var history =
                    historyRepository.findByUetrOrderByChangedAtAsc(
                            payment.uetr()
                    );

            assertThat(history).hasSize(1);

            TransactionLedger updatedLedger =
                    ledgerRepository.findById(payment.uetr())
                            .orElseThrow();

            assertThat(updatedLedger.getStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.PROCESSING
                    );

        } finally {
            executor.shutdownNow();
        }
    }


    // --------------------------------------------------
    // TEST 5: Concurrent conflicting ACK and NACK
    // --------------------------------------------------

    @Test
    void shouldRejectOneOfTwoConcurrentConflictingResponses()
            throws Exception {

        TestPayment payment =
                createPendingPayment("CONCURRENT-CONFLICT-");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Future<String> ackRequest = executor.submit(() -> {

                ready.countDown();
                start.await();

                try {

                    networkResponseService.processDelayedResponse(
                            payment.submissionId(),
                            NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                            "Concurrent ACK"
                    );

                    return "SUCCESS";

                } catch (IllegalStateException exception) {
                    return "CONFLICT";
                }
            });

            Future<String> nackRequest = executor.submit(() -> {

                ready.countDown();
                start.await();

                try {

                    networkResponseService.processDelayedResponse(
                            payment.submissionId(),
                            NetworkResponse.ResponseStatus.SIMULATED_NACK,
                            "Concurrent NACK"
                    );

                    return "SUCCESS";

                } catch (IllegalStateException exception) {
                    return "CONFLICT";
                }
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            String ackResult =
                    ackRequest.get(15, TimeUnit.SECONDS);

            String nackResult =
                    nackRequest.get(15, TimeUnit.SECONDS);

            assertThat(List.of(ackResult, nackResult))
                    .containsExactlyInAnyOrder(
                            "SUCCESS",
                            "CONFLICT"
                    );

            NetworkResponse storedResponse =
                    responseRepository
                            .findByNetworkSubmission_Id(
                                    payment.submissionId()
                            )
                            .orElseThrow();

            TransactionLedger updatedLedger =
                    ledgerRepository.findById(payment.uetr())
                            .orElseThrow();

            if (storedResponse.getStatus()
                    == NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED) {

                assertThat(updatedLedger.getStatus())
                        .isEqualTo(
                                TransactionLedger.TransactionStatus.PROCESSING
                        );

            } else {

                assertThat(storedResponse.getStatus())
                        .isEqualTo(
                                NetworkResponse.ResponseStatus.SIMULATED_NACK
                        );

                assertThat(updatedLedger.getStatus())
                        .isEqualTo(
                                TransactionLedger.TransactionStatus.REJECTED
                        );
            }

            var history =
                    historyRepository.findByUetrOrderByChangedAtAsc(
                            payment.uetr()
                    );

            assertThat(history).hasSize(1);

        } finally {
            executor.shutdownNow();
        }
    }


    // --------------------------------------------------
    // TEST 6: Transaction rollback on history failure
    // --------------------------------------------------

    @Test
    void shouldRollbackEntireTransactionWhenHistoryPersistenceFails() {

        TestPayment payment =
                createPendingPayment("ROLLBACK-TEST-");

        doThrow(new RuntimeException(
                "Simulated history persistence failure"
        ))
                .when(historyRepository)
                .save(any(PaymentStatusHistory.class));

        assertThatThrownBy(() ->
                networkResponseService.processDelayedResponse(
                        payment.submissionId(),
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        "Delayed ACK for rollback test"
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated history persistence failure");

        entityManager.clear();

        // Network response must have been rolled back.
        assertThat(
                responseRepository.findByNetworkSubmission_Id(
                        payment.submissionId()
                )
        ).isEmpty();

        // Payment status must remain unchanged.
        TransactionLedger ledger =
                ledgerRepository.findById(payment.uetr()).orElseThrow();

        assertThat(ledger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.NETWORK_PENDING
                );

        // No history entry must have been persisted.
        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(
                        payment.uetr()
                );

        assertThat(history).isEmpty();
    }


    // --------------------------------------------------
    // HELPER: Create a payment waiting for network response
    // --------------------------------------------------

    private TestPayment createPendingPayment(String transactionPrefix) {

        String uetr = UUID.randomUUID().toString();

        TransactionLedger ledger = new TransactionLedger();
        ledger.setUetr(uetr);
        ledger.setTransactionId(
                transactionPrefix + UUID.randomUUID()
        );
        ledger.setAmount(new BigDecimal("2500.00"));
        ledger.setCurrency("USD");
        ledger.setStatus(
                TransactionLedger.TransactionStatus.NETWORK_PENDING
        );

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

        return new TestPayment(uetr, submission.getId());
    }

    private record TestPayment(String uetr, Long submissionId) {}
}
