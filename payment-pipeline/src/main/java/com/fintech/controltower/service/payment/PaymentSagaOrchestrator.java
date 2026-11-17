
package com.fintech.controltower.service.payment;

import com.fintech.controltower.domain.entity.IsoMessage;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.entity.PaymentStatusHistory;
import com.fintech.controltower.domain.entity.TransactionLedger;

import com.fintech.controltower.domain.model.PaymentRequest;

import com.fintech.controltower.domain.repository.IsoMessageRepository;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;

import com.fintech.controltower.exception.AmlHoldException;

import com.fintech.controltower.financial.model.PaymentCalculationRequest;
import com.fintech.controltower.financial.model.PaymentCalculationResult;
import com.fintech.controltower.financial.service.FinancialCalculationService;

import com.fintech.controltower.service.aml.AmlScreeningService;
import com.fintech.controltower.service.network.NetworkAdapterRegistry;
import com.fintech.controltower.service.network.NetworkSubmissionResult;
import com.fintech.controltower.service.network.PaymentNetworkAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentSagaOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentSagaOrchestrator.class);

    private final TransactionLedgerRepository ledgerRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final IsoMessageRepository isoMessageRepository;
    private final NetworkSubmissionRepository networkSubmissionRepository;

    private final AmlScreeningService amlScreeningService;
    private final FinancialCalculationService financialCalculationService;
    private final NetworkAdapterRegistry networkAdapterRegistry;
    private final TransactionStatusService transactionStatusService;

    private final TransactionTemplate transactionTemplate;

    public PaymentSagaOrchestrator(
            TransactionLedgerRepository ledgerRepository,
            PaymentStatusHistoryRepository historyRepository,
            IsoMessageRepository isoMessageRepository,
            NetworkSubmissionRepository networkSubmissionRepository,
            AmlScreeningService amlScreeningService,
            FinancialCalculationService financialCalculationService,
            NetworkAdapterRegistry networkAdapterRegistry,
            TransactionStatusService transactionStatusService,
            PlatformTransactionManager transactionManager
    ) {
        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
        this.isoMessageRepository = isoMessageRepository;
        this.networkSubmissionRepository = networkSubmissionRepository;
        this.amlScreeningService = amlScreeningService;
        this.financialCalculationService = financialCalculationService;
        this.networkAdapterRegistry = networkAdapterRegistry;
        this.transactionStatusService = transactionStatusService;

        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /*
     * The orchestrator itself must not hold a database transaction.
     * Each local persistence operation starts its own transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentSagaResult executePaymentSaga(
            PaymentRequest request,
            String uetr
    ) {

        // 1. Calculate the financial instruction outside a DB transaction.
        PaymentCalculationRequest calculationRequest =
                new PaymentCalculationRequest(
                        request.debtorCountryCode(),
                        request.creditorCountryCode(),
                        request.sourceCurrency(),
                        request.destinationCurrency(),
                        request.amountMode(),
                        request.enteredAmount()
                );

        PaymentCalculationResult calculation =
                financialCalculationService.calculate(calculationRequest);

        // 2. Perform AML screening outside a DB transaction.
        AmlScreeningService.AmlScreeningResult screeningResult =
                amlScreeningService.screenTransaction(request);

        if (screeningResult == null) {
            throw new IllegalStateException(
                    "AML screening returned no result."
            );
        }

        String routeType = calculation.routeType();

        // Resolve the adapter before accepting the payment.
        PaymentNetworkAdapter adapter = null;

        if (!screeningResult.hold()) {
            adapter = networkAdapterRegistry.getAdapter(routeType);
        }

        // 3. Transaction 1: persist the initial payment atomically.
        final PaymentNetworkAdapter selectedAdapter = adapter;

        transactionTemplate.executeWithoutResult(transactionStatus ->
                persistInitialPayment(
                        request,
                        uetr,
                        calculation,
                        screeningResult
                )
        );

        // The AML hold is committed before this exception is thrown.
        if (screeningResult.hold()) {
            throw new AmlHoldException(
                    uetr,
                    screeningResult.reason()
            );
        }

        // 4. Network submission occurs OUTSIDE a database transaction.
        NetworkSubmissionResult submissionResult;

        try {

            submissionResult = selectedAdapter.submit(
                    request,
                    calculation,
                    uetr
            );

            validateSubmissionResult(
                    routeType,
                    submissionResult
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Network submission outcome is uncertain. UETR: {}",
                    uetr,
                    exception
            );

            return markNetworkPending(
                    uetr,
                    "Network submission outcome is unknown. "
                            + "Manual reconciliation is required.",
                    "Network submission could not be confirmed. "
                            + "Payment is awaiting reconciliation. UETR: "
                            + uetr,
                    exception
            );
        }

        // 5. Transaction 2: persist network evidence and status atomically.
        try {

            transactionTemplate.executeWithoutResult(transactionStatus ->
                    persistNetworkOutcome(
                            uetr,
                            routeType,
                            submissionResult
                    )
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Network response was received but could not be "
                            + "persisted. UETR: {}",
                    uetr,
                    exception
            );

            return markNetworkPending(
                    uetr,
                    "Network response was received but could not be "
                            + "persisted. Manual reconciliation is required.",
                    "Network response could not be fully persisted. "
                            + "Payment is awaiting reconciliation. UETR: "
                            + uetr,
                    exception
            );
        }

        // 6. Return the outcome after the persistence transaction commits.
        return createSagaResult(uetr, submissionResult);
    }

    /*
     * Transaction 1:
     * Create the ledger and its initial history together.
     */
    private void persistInitialPayment(
            PaymentRequest request,
            String uetr,
            PaymentCalculationResult calculation,
            AmlScreeningService.AmlScreeningResult screeningResult
    ) {

        TransactionLedger ledger = new TransactionLedger();

        ledger.setUetr(uetr);
        ledger.setTransactionId(request.transactionId());

        // Sender details
        ledger.setDebtorName(request.debtorName());
        ledger.setDebtorAccountNumber(request.debtorAccountNumber());
        ledger.setDebtorBic(request.debtorBic());
        ledger.setDebtorCountryCode(request.debtorCountryCode());
        ledger.setDebtorClearingSystemCode(
                request.debtorClearingSystemCode()
        );
        ledger.setDebtorClearingMemberId(
                request.debtorClearingMemberId()
        );

        // Beneficiary details
        ledger.setCreditorName(request.creditorName());
        ledger.setCreditorAccountNumber(request.creditorAccountNumber());
        ledger.setCreditorBic(request.creditorBic());
        ledger.setCreditorCountryCode(request.creditorCountryCode());
        ledger.setCreditorClearingSystemCode(
                request.creditorClearingSystemCode()
        );
        ledger.setCreditorClearingMemberId(
                request.creditorClearingMemberId()
        );

        // Calculated financial instruction
        ledger.setAmount(calculation.sourcePrincipal());
        ledger.setCurrency(calculation.sourceCurrency());

        ledger.setAmountMode(calculation.amountMode());
        ledger.setDestinationAmount(calculation.destinationAmount());
        ledger.setDestinationCurrency(calculation.destinationCurrency());
        ledger.setExchangeRate(calculation.exchangeRate());
        ledger.setTransferFee(calculation.transferFee());
        ledger.setFeeCurrency(calculation.feeCurrency());
        ledger.setTotalSenderDebit(calculation.totalSenderDebit());

        TransactionLedger.TransactionStatus initialStatus;
        String historyReason;

        if (screeningResult.hold()) {

            initialStatus =
                    TransactionLedger.TransactionStatus.SUSPENDED_AML_HOLD;

            historyReason = screeningResult.reason();

        } else {

            initialStatus =
                    TransactionLedger.TransactionStatus.ACCEPTED;

            historyReason =
                    "Payment accepted after successful AML screening";
        }

        ledger.setStatus(initialStatus);

        ledgerRepository.save(ledger);

        historyRepository.save(
                new PaymentStatusHistory(
                        uetr,
                        null,
                        initialStatus,
                        historyReason
                )
        );
    }

    /*
     * Validate the adapter response before persisting it.
     */
    private void validateSubmissionResult(
            String expectedRoute,
            NetworkSubmissionResult result
    ) {

        if (result == null) {
            throw new IllegalStateException(
                    "Network adapter returned no submission result."
            );
        }

        if (!expectedRoute.equals(result.routeType())) {
            throw new IllegalStateException(
                    "Network adapter returned an unexpected route type."
            );
        }

        if (result.status() == null) {
            throw new IllegalStateException(
                    "Network adapter returned no submission status."
            );
        }

        if ("CROSS_BORDER_CORRESPONDENT".equals(expectedRoute)
                && (result.isoXmlMessage() == null
                || result.isoXmlMessage().isBlank())) {

            throw new IllegalStateException(
                    "Cross-border adapter did not return an ISO 20022 message."
            );
        }
    }

    /*
     * Transaction 2:
     * Persist the network submission, ISO message, payment status
     * and status history as one atomic operation.
     */
    private void persistNetworkOutcome(
            String uetr,
            String routeType,
            NetworkSubmissionResult result
    ) {

        TransactionLedger ledger =
                ledgerRepository.findByUetrForUpdate(uetr)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment not found for UETR: " + uetr
                                )
                        );

        NetworkSubmission networkSubmission =
                new NetworkSubmission(
                        ledger,
                        result.routeType(),
                        result.networkReference(),
                        NetworkSubmission.SubmissionStatus.valueOf(
                                result.status().name()
                        ),
                        result.submittedAt(),
                        result.respondedAt(),
                        result.responseMessage()
                );

        networkSubmissionRepository.save(networkSubmission);

        String isoXmlMessage = result.isoXmlMessage();

        if (isoXmlMessage != null && !isoXmlMessage.isBlank()) {

            IsoMessage isoMessage = new IsoMessage();

            isoMessage.setTransactionLedger(ledger);
            isoMessage.setMessageType("pacs.008");
            isoMessage.setSchemaVersion("001.14");
            isoMessage.setMessageContent(isoXmlMessage);
            isoMessage.setValidationStatus("VALIDATED");

            isoMessageRepository.save(isoMessage);
        }

        TransactionLedger.TransactionStatus newStatus;
        String reason;

        switch (result.status()) {

            case SIMULATED_ACKNOWLEDGED -> {

                newStatus =
                        TransactionLedger.TransactionStatus.PROCESSING;

                reason =
                        "Simulated network acknowledged the payment instruction. "
                                + "Network reference: "
                                + result.networkReference();
            }

            case SIMULATED_NACK -> {

                newStatus =
                        TransactionLedger.TransactionStatus.REJECTED;

                reason =
                        "Simulated network rejected the payment instruction. "
                                + "Network reference: "
                                + result.networkReference();
            }

            case SIMULATED_TIMEOUT -> {

                newStatus =
                        TransactionLedger.TransactionStatus.NETWORK_PENDING;

                reason =
                        "No simulated network response was received. "
                                + "Payment is awaiting confirmation. "
                                + "Network reference: "
                                + result.networkReference();
            }

            default -> throw new IllegalStateException(
                    "Unsupported network submission status."
            );
        }

        // Joins Transaction 2 because TransactionStatusService
        // uses Spring's default REQUIRED propagation.
        transactionStatusService.updateStatus(
                uetr,
                newStatus,
                reason
        );
    }

    /*
     * If the network outcome is uncertain, preserve the payment
     * as NETWORK_PENDING rather than incorrectly marking it FAILED.
     */
    private PaymentSagaResult markNetworkPending(
            String uetr,
            String reason,
            String message,
            RuntimeException originalException
    ) {

        try {

            transactionTemplate.executeWithoutResult(transactionStatus ->
                    transactionStatusService.updateStatus(
                            uetr,
                            TransactionLedger.TransactionStatus.NETWORK_PENDING,
                            reason
                    )
            );

        } catch (RuntimeException recoveryException) {

            originalException.addSuppressed(recoveryException);
            throw originalException;
        }

        return new PaymentSagaResult(
                PaymentSagaResult.Outcome.NETWORK_PENDING,
                message
        );
    }

    private PaymentSagaResult createSagaResult(
            String uetr,
            NetworkSubmissionResult result
    ) {

        return switch (result.status()) {

            case SIMULATED_ACKNOWLEDGED -> {

                log.info("Network acknowledged payment. UETR: {}", uetr);

                yield new PaymentSagaResult(
                        PaymentSagaResult.Outcome.ACKNOWLEDGED,
                        "Payment accepted for simulated processing. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + result.networkReference()
                );
            }

            case SIMULATED_NACK -> {

                log.info("Network rejected payment. UETR: {}", uetr);

                yield new PaymentSagaResult(
                        PaymentSagaResult.Outcome.REJECTED,
                        "Simulated network rejected the payment. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + result.networkReference()
                );
            }

            case SIMULATED_TIMEOUT -> {

                log.info("Network timeout. Payment pending. UETR: {}", uetr);

                yield new PaymentSagaResult(
                        PaymentSagaResult.Outcome.NETWORK_PENDING,
                        "No simulated network response was received. "
                                + "Payment is awaiting confirmation. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + result.networkReference()
                );
            }
        };
    }
}
