
package com.fintech.controltower.service.payment;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.entity.CustomerRole;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;
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
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

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

        Customer customer = new Customer(
                "Test Sender",
                "saga-test-" + UUID.randomUUID() + "@example.com",
                "test-password-hash",
                "9999999999",
                "IN",
                "Pune",
                CustomerRole.CUSTOMER
        );

        customer = customerRepository.saveAndFlush(customer);

        CustomerAccount account = new CustomerAccount(
                "IN0000000001",
                customer,
                "Test Sender",
                CustomerAccount.AccountType.SAVINGS,
                "INR"
        );

        account.credit(new BigDecimal("10000.00"));
        account = accountRepository.saveAndFlush(account);

        Long customerId = customer.getId();
        Long accountId = account.getId();

        PaymentRequest request = new PaymentRequest(
                "TX-TEST-" + UUID.randomUUID(),
                "Test Sender",
                account.getAccountNumber(),
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

        doAnswer(invocation -> {

            assertThat(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            ).isFalse();

            TransactionLedger persistedLedger =
                    ledgerRepository.findById(uetr).orElseThrow();

            assertThat(persistedLedger.getStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.ACCEPTED
                    );

            CustomerAccount persistedAccount =
                    accountRepository.findById(accountId).orElseThrow();

            assertThat(persistedAccount.getCurrentBalance())
                    .isEqualByComparingTo("10000.00");

            assertThat(persistedAccount.getReservedBalance())
                    .isEqualByComparingTo(
                            persistedLedger.getTotalSenderDebit()
                    );

            assertThat(persistedAccount.getAvailableBalance())
                    .isEqualByComparingTo(
                            new BigDecimal("10000.00")
                                    .subtract(
                                            persistedLedger.getTotalSenderDebit()
                                    )
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

        PaymentSagaResult result =
                paymentSagaOrchestrator.executePaymentSaga(
                        request,
                        uetr,
                        customerId,
                        accountId
                );

        assertThat(transactionBoundaryVerified.get()).isTrue();

        assertThat(result.outcome())
                .isEqualTo(PaymentSagaResult.Outcome.ACKNOWLEDGED);

        TransactionLedger finalLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(finalLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.PROCESSING
                );

        CustomerAccount finalAccount =
                accountRepository.findById(accountId).orElseThrow();

        assertThat(finalAccount.getCurrentBalance())
                .isEqualByComparingTo("10000.00");

        assertThat(finalAccount.getReservedBalance())
                .isEqualByComparingTo(
                        finalLedger.getTotalSenderDebit()
                );

        assertThat(finalAccount.getAvailableBalance())
                .isEqualByComparingTo(
                        new BigDecimal("10000.00")
                                .subtract(finalLedger.getTotalSenderDebit())
                );

        var submissions =
                submissionRepository
                        .findByTransactionLedger_UetrOrderBySubmittedAtAsc(
                                uetr
                        );

        assertThat(submissions).hasSize(1);

        assertThat(submissions.get(0).getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_ACKNOWLEDGED
                );

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


    @Test
    void shouldReleaseReservedFundsWhenImmediateNackHasDatabaseScaleAmount() {

        String uetr = UUID.randomUUID().toString();

        Customer customer = new Customer(
                "NACK Test Sender",
                "saga-nack-" + UUID.randomUUID() + "@example.com",
                "test-password-hash",
                "9999999998",
                "IN",
                "Pune",
                CustomerRole.CUSTOMER
        );

        customer = customerRepository.saveAndFlush(customer);

        String accountNumber =
                "IN" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 10);

        CustomerAccount account = new CustomerAccount(
                accountNumber,
                customer,
                "NACK Test Sender",
                CustomerAccount.AccountType.SAVINGS,
                "INR"
        );

        account.credit(new BigDecimal("10000.00"));
        account = accountRepository.saveAndFlush(account);

        Long customerId = customer.getId();
        Long accountId = account.getId();

        PaymentRequest request = new PaymentRequest(
                "TX-NACK-" + UUID.randomUUID(),
                "NACK Test Sender",
                account.getAccountNumber(),
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

        AtomicBoolean databaseScaleVerified =
                new AtomicBoolean(false);

        doAnswer(invocation -> {

            // Initial reservation must already be committed.
            assertThat(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            ).isFalse();

            // Reload the ledger from the database after initial persistence.
            TransactionLedger persistedLedger =
                    ledgerRepository.findById(uetr).orElseThrow();

            assertThat(persistedLedger.getStatus())
                    .isEqualTo(
                            TransactionLedger.TransactionStatus.ACCEPTED
                    );

            assertThat(persistedLedger.getFundsReservationStatus())
                    .isEqualTo(
                            TransactionLedger.FundsReservationStatus.RESERVED
                    );

            assertThat(persistedLedger.getTotalSenderDebit())
                    .isNotNull();

            // The database column is defined with scale 5.
            assertThat(persistedLedger.getTotalSenderDebit().scale())
                    .isEqualTo(5);

            CustomerAccount persistedAccount =
                    accountRepository.findById(accountId).orElseThrow();

            assertThat(persistedAccount.getReservedBalance())
                    .isEqualByComparingTo(
                            persistedLedger.getTotalSenderDebit()
                    );

            databaseScaleVerified.set(true);

            LocalDateTime now = LocalDateTime.now();

            return new NetworkSubmissionResult(
                    "DOMESTIC_CLEARING",
                    "TEST-NACK-" + UUID.randomUUID(),
                    NetworkSubmissionResult.SubmissionStatus.SIMULATED_NACK,
                    now,
                    now,
                    "Test network rejected payment",
                    null
            );

        }).when(domesticClearingAdapter).submit(
                any(PaymentRequest.class),
                any(),
                eq(uetr)
        );

        PaymentSagaResult result =
                paymentSagaOrchestrator.executePaymentSaga(
                        request,
                        uetr,
                        customerId,
                        accountId
                );

        assertThat(databaseScaleVerified.get()).isTrue();

        assertThat(result.outcome())
                .isEqualTo(PaymentSagaResult.Outcome.REJECTED);

        TransactionLedger finalLedger =
                ledgerRepository.findById(uetr).orElseThrow();

        assertThat(finalLedger.getStatus())
                .isEqualTo(
                        TransactionLedger.TransactionStatus.REJECTED
                );

        assertThat(finalLedger.getFundsReservationStatus())
                .isEqualTo(
                        TransactionLedger.FundsReservationStatus.RELEASED
                );

        CustomerAccount finalAccount =
                accountRepository.findById(accountId).orElseThrow();

        assertThat(finalAccount.getCurrentBalance())
                .isEqualByComparingTo("10000.00");

        assertThat(finalAccount.getReservedBalance())
                .isEqualByComparingTo("0.00");

        assertThat(finalAccount.getAvailableBalance())
                .isEqualByComparingTo("10000.00");

        var submissions =
                submissionRepository
                        .findByTransactionLedger_UetrOrderBySubmittedAtAsc(
                                uetr
                        );

        assertThat(submissions).hasSize(1);

        assertThat(submissions.get(0).getStatus())
                .isEqualTo(
                        NetworkSubmission.SubmissionStatus.SIMULATED_NACK
                );

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
                        TransactionLedger.TransactionStatus.REJECTED
                );
    }
}
