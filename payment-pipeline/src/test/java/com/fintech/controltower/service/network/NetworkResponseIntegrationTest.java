
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.entity.CustomerRole;
import com.fintech.controltower.domain.entity.NetworkResponse;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.entity.PaymentStatusHistory;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;
import com.fintech.controltower.domain.repository.NetworkResponseRepository;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;
import com.fintech.controltower.service.payment.TransactionStatusService;

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
    private TransactionStatusService transactionStatusService;

    @Autowired
    private TransactionLedgerRepository ledgerRepository;

    @Autowired
    private NetworkSubmissionRepository submissionRepository;

    @Autowired
    private NetworkResponseRepository responseRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

    @SpyBean
    private PaymentStatusHistoryRepository historyRepository;

    @Autowired
    private EntityManager entityManager;


    // --------------------------------------------------
    // TEST 1: Delayed ACK
    // --------------------------------------------------

    @Test
    void shouldPersistDelayedAcknowledgementAndUpdatePaymentStatus() {

        TestPayment payment =
                createPendingPayment("INTEGRATION-TEST-");

        String uetr = payment.uetr();
        Long submissionId = payment.submissionId();

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
    // TEST 2: Delayed NACK releases reserved funds
    // --------------------------------------------------

    @Test
    void shouldPersistDelayedRejectionAndReleaseReservedFunds() {

        TestPayment payment =
                createPendingPayment(
                        "INTEGRATION-NACK-",
                        new BigDecimal("1800.00")
                );

        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        payment.submissionId(),
                        NetworkResponse.ResponseStatus.SIMULATED_NACK,
                        "Payment rejected by correspondent bank"
                );

        assertThat(response.getId()).isNotNull();

        NetworkResponse storedResponse =
                responseRepository
                        .findByNetworkSubmission_Id(payment.submissionId())
                        .orElseThrow();

        assertThat(storedResponse.getStatus())
                .isEqualTo(
                        NetworkResponse.ResponseStatus.SIMULATED_NACK
                );

        TransactionLedger updatedLedger =
                ledgerRepository.findById(payment.uetr()).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.REJECTED
                );

        assertThat(updatedLedger.getFundsReservationStatus())
                .isEqualTo(
                        TransactionLedger.FundsReservationStatus.RELEASED
                );

        CustomerAccount updatedAccount =
                accountRepository.findById(payment.accountId())
                        .orElseThrow();

        assertThat(updatedAccount.getCurrentBalance())
                .isEqualByComparingTo("10000.00");

        assertThat(updatedAccount.getReservedBalance())
                .isEqualByComparingTo("0.00");

        assertThat(updatedAccount.getAvailableBalance())
                .isEqualByComparingTo("10000.00");

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(
                        payment.uetr()
                );

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
                submissionRepository.findById(payment.submissionId())
                        .orElseThrow();

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

        TestPayment payment =
                createPendingPayment("INTEGRATION-DUPLICATE-");

        String uetr = payment.uetr();
        Long submissionId = payment.submissionId();

        String responseMessage =
                "Delayed ACK received from correspondent bank";

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

            assertThat(updatedLedger.getFundsReservationStatus())
                    .isEqualTo(
                            TransactionLedger.FundsReservationStatus.RESERVED
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

            CustomerAccount updatedAccount =
                    accountRepository.findById(payment.accountId())
                            .orElseThrow();

            if (storedResponse.getStatus()
                    == NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED) {

                assertThat(updatedLedger.getStatus())
                        .isEqualTo(
                                TransactionLedger.TransactionStatus.PROCESSING
                        );

                assertThat(updatedLedger.getFundsReservationStatus())
                        .isEqualTo(
                                TransactionLedger.FundsReservationStatus.RESERVED
                        );

                assertThat(updatedAccount.getReservedBalance())
                        .isEqualByComparingTo("2500.00");

            } else {

                assertThat(storedResponse.getStatus())
                        .isEqualTo(
                                NetworkResponse.ResponseStatus.SIMULATED_NACK
                        );

                assertThat(updatedLedger.getStatus())
                        .isEqualTo(
                                TransactionLedger.TransactionStatus.REJECTED
                        );

                assertThat(updatedLedger.getFundsReservationStatus())
                        .isEqualTo(
                                TransactionLedger.FundsReservationStatus.RELEASED
                        );

                assertThat(updatedAccount.getCurrentBalance())
                        .isEqualByComparingTo("10000.00");

                assertThat(updatedAccount.getReservedBalance())
                        .isEqualByComparingTo("0.00");
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

        assertThat(
                responseRepository.findByNetworkSubmission_Id(
                        payment.submissionId()
                )
        ).isEmpty();

        TransactionLedger ledger =
                ledgerRepository.findById(payment.uetr()).orElseThrow();

        assertThat(ledger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.NETWORK_PENDING
                );

        assertThat(ledger.getFundsReservationStatus())
                .isEqualTo(
                        TransactionLedger.FundsReservationStatus.RESERVED
                );

        CustomerAccount account =
                accountRepository.findById(payment.accountId())
                        .orElseThrow();

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("10000.00");

        assertThat(account.getReservedBalance())
                .isEqualByComparingTo("2500.00");

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(
                        payment.uetr()
                );

        assertThat(history).isEmpty();
    }


    // --------------------------------------------------
    // TEST 7: Successful settlement debits reserved funds
    // --------------------------------------------------

    @Test
    void shouldDebitReservedFundsWhenPaymentIsSettled() {

        TestPayment payment =
                createPendingPayment("SETTLEMENT-SUCCESS-");

        // Move payment from NETWORK_PENDING to PROCESSING.
        transactionStatusService.updateStatus(
                payment.uetr(),
                TransactionLedger.TransactionStatus.PROCESSING,
                "Network ACK received"
        );

        // Settle the payment.
        transactionStatusService.updateStatus(
                payment.uetr(),
                TransactionLedger.TransactionStatus.SETTLED,
                "Payment settled successfully"
        );

        entityManager.clear();

        TransactionLedger updatedLedger =
                ledgerRepository.findById(payment.uetr()).orElseThrow();

        CustomerAccount updatedAccount =
                accountRepository.findById(payment.accountId())
                        .orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.SETTLED
                );

        assertThat(updatedLedger.getFundsReservationStatus())
                .isEqualTo(
                        TransactionLedger.FundsReservationStatus.DEBITED
                );

        assertThat(updatedAccount.getCurrentBalance())
                .isEqualByComparingTo("7500.00");

        assertThat(updatedAccount.getReservedBalance())
                .isEqualByComparingTo("0.00");

        assertThat(updatedAccount.getAvailableBalance())
                .isEqualByComparingTo("7500.00");

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(
                        payment.uetr()
                );

        assertThat(history).hasSize(2);

        assertThat(history.get(0).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        assertThat(history.get(1).getPreviousStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        assertThat(history.get(1).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.SETTLED
                );
    }


    // --------------------------------------------------
    // TEST 8: Rollback settlement if history persistence fails
    // --------------------------------------------------

    @Test
    void shouldRollbackSettlementWhenHistoryPersistenceFails() {

        TestPayment payment =
                createPendingPayment("SETTLEMENT-ROLLBACK-");

        // First move payment to PROCESSING successfully.
        transactionStatusService.updateStatus(
                payment.uetr(),
                TransactionLedger.TransactionStatus.PROCESSING,
                "Network ACK received"
        );

        // Simulate failure while saving settlement history.
        doThrow(new RuntimeException(
                "Simulated settlement history failure"
        ))
                .when(historyRepository)
                .save(any(PaymentStatusHistory.class));

        assertThatThrownBy(() ->
                transactionStatusService.updateStatus(
                        payment.uetr(),
                        TransactionLedger.TransactionStatus.SETTLED,
                        "Payment settlement"
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated settlement history failure");

        entityManager.clear();

        TransactionLedger updatedLedger =
                ledgerRepository.findById(payment.uetr()).orElseThrow();

        assertThat(updatedLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        assertThat(updatedLedger.getFundsReservationStatus())
                .isEqualTo(
                        TransactionLedger.FundsReservationStatus.RESERVED
                );

        CustomerAccount updatedAccount =
                accountRepository.findById(payment.accountId())
                        .orElseThrow();

        assertThat(updatedAccount.getCurrentBalance())
                .isEqualByComparingTo("10000.00");

        assertThat(updatedAccount.getReservedBalance())
                .isEqualByComparingTo("2500.00");

        var history =
                historyRepository.findByUetrOrderByChangedAtAsc(
                        payment.uetr()
                );

        assertThat(history).hasSize(1);

        assertThat(history.get(0).getNewStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );
    }


    // --------------------------------------------------
    // TEST 9: Concurrent settlement cannot debit twice
    // --------------------------------------------------

    @Test
    void shouldPreventDoubleDebitDuringConcurrentSettlement()
            throws Exception {

        TestPayment payment =
                createPendingPayment("SETTLEMENT-CONCURRENT-");

        // Prepare payment for settlement.
        transactionStatusService.updateStatus(
                payment.uetr(),
                TransactionLedger.TransactionStatus.PROCESSING,
                "Network ACK received"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            var settlementRequest =
                    (java.util.concurrent.Callable<String>) () -> {

                        ready.countDown();
                        start.await();

                        try {

                            transactionStatusService.updateStatus(
                                    payment.uetr(),
                                    TransactionLedger.TransactionStatus.SETTLED,
                                    "Concurrent settlement request"
                            );

                            return "SUCCESS";

                        } catch (IllegalStateException exception) {
                            return "REJECTED";
                        }
                    };

            Future<String> first =
                    executor.submit(settlementRequest);

            Future<String> second =
                    executor.submit(settlementRequest);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            String firstResult =
                    first.get(15, TimeUnit.SECONDS);

            String secondResult =
                    second.get(15, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult))
                    .containsExactlyInAnyOrder(
                            "SUCCESS",
                            "REJECTED"
                    );

            entityManager.clear();

            TransactionLedger updatedLedger =
                    ledgerRepository.findById(payment.uetr())
                            .orElseThrow();

            CustomerAccount updatedAccount =
                    accountRepository.findById(payment.accountId())
                            .orElseThrow();

            assertThat(updatedLedger.getStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.SETTLED
                    );

            assertThat(updatedLedger.getFundsReservationStatus())
                    .isEqualTo(
                            TransactionLedger.FundsReservationStatus.DEBITED
                    );

            assertThat(updatedAccount.getCurrentBalance())
                    .isEqualByComparingTo("7500.00");

            assertThat(updatedAccount.getReservedBalance())
                    .isEqualByComparingTo("0.00");

            var history =
                    historyRepository.findByUetrOrderByChangedAtAsc(
                            payment.uetr()
                    );

            assertThat(history).hasSize(2);

        } finally {
            executor.shutdownNow();
        }
    }


    // --------------------------------------------------
    // HELPER: Create a timed-out network submission
    // --------------------------------------------------

    private NetworkSubmission createTimedOutSubmission(
            TransactionLedger ledger
    ) {

        NetworkSubmission submission = new NetworkSubmission(
                ledger,
                "CROSS_BORDER_CORRESPONDENT",
                "SIM-TEST-" + UUID.randomUUID(),
                NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT,
                LocalDateTime.now().minusMinutes(5),
                null,
                "Original network request timed out"
        );

        return submissionRepository.saveAndFlush(submission);
    }


    // --------------------------------------------------
    // HELPER: Create a payment with genuinely reserved funds
    // --------------------------------------------------

    private TestPayment createPendingPayment(String transactionPrefix) {

        return createPendingPayment(
                transactionPrefix,
                new BigDecimal("2500.00")
        );
    }

    private TestPayment createPendingPayment(
            String transactionPrefix,
            BigDecimal senderDebit
    ) {

        String uetr = UUID.randomUUID().toString();

        // Create a unique customer for this integration test.
        String uniqueId = UUID.randomUUID().toString();

        Customer customer = new Customer(
                "Integration Test Customer",
                "integration-" + uniqueId + "@example.com",
                "test-password-hash",
                "9000000000",
                "IN",
                "Pune",
                CustomerRole.CUSTOMER
        );

        customer = customerRepository.saveAndFlush(customer);

        // Create and fund the customer's account.
        CustomerAccount account = new CustomerAccount(
                "ACC" + uniqueId.replace("-", "").substring(0, 17),
                customer,
                "Integration Test Customer",
                CustomerAccount.AccountType.SAVINGS,
                "USD"
        );

        account.credit(new BigDecimal("10000.00"));

        account = accountRepository.saveAndFlush(account);

        // Reserve the sender debit before creating the pending payment.
        account.reserve(senderDebit);

        account = accountRepository.saveAndFlush(account);

        // Create the payment ledger with its actual sender account.
        TransactionLedger ledger = new TransactionLedger();

        ledger.setUetr(uetr);
        ledger.setTransactionId(
                transactionPrefix + UUID.randomUUID()
        );
        ledger.setAmount(senderDebit);
        ledger.setCurrency("USD");
        ledger.setTotalSenderDebit(senderDebit);
        ledger.setSenderAccount(account);
        ledger.setStatus(
                TransactionLedger.TransactionStatus.NETWORK_PENDING
        );
        ledger.markFundsReserved();

        ledger = ledgerRepository.saveAndFlush(ledger);

        NetworkSubmission submission =
                createTimedOutSubmission(ledger);

        return new TestPayment(
                uetr,
                submission.getId(),
                account.getId()
        );
    }


    private record TestPayment(
            String uetr,
            Long submissionId,
            Long accountId
    ) {
    }
}
