
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSagaOrchestrator {

    private final TransactionLedgerRepository ledgerRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final IsoMessageRepository isoMessageRepository;
    private final NetworkSubmissionRepository networkSubmissionRepository;
    private final AmlScreeningService amlScreeningService;
    private final FinancialCalculationService financialCalculationService;
    private final NetworkAdapterRegistry networkAdapterRegistry;
    private final TransactionStatusService transactionStatusService;

    public PaymentSagaOrchestrator(
            TransactionLedgerRepository ledgerRepository,
            PaymentStatusHistoryRepository historyRepository,
            IsoMessageRepository isoMessageRepository,
            NetworkSubmissionRepository networkSubmissionRepository,
            AmlScreeningService amlScreeningService,
            FinancialCalculationService financialCalculationService,
            NetworkAdapterRegistry networkAdapterRegistry,
            TransactionStatusService transactionStatusService
    ) {
        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
        this.isoMessageRepository = isoMessageRepository;
        this.networkSubmissionRepository = networkSubmissionRepository;
        this.amlScreeningService = amlScreeningService;
        this.financialCalculationService = financialCalculationService;
        this.networkAdapterRegistry = networkAdapterRegistry;
        this.transactionStatusService = transactionStatusService;
    }

    @Transactional(noRollbackFor = SecurityException.class)
    public PaymentSagaResult executePaymentSaga(
            PaymentRequest request,
            String uetr
    ) {

        // 1. Calculate the financial instruction exactly once.
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

        // 2. Initialize the transaction ledger.
        TransactionLedger ledger = new TransactionLedger();

        ledger.setUetr(uetr);
        ledger.setTransactionId(request.transactionId());

        // 3. Map sender details.
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

        // 4. Map beneficiary details.
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

        // 5. Persist the calculated financial instruction.
        // amount and currency represent source principal and source currency.
        ledger.setAmount(calculation.sourcePrincipal());
        ledger.setCurrency(calculation.sourceCurrency());

        ledger.setAmountMode(calculation.amountMode());
        ledger.setDestinationAmount(calculation.destinationAmount());
        ledger.setDestinationCurrency(calculation.destinationCurrency());
        ledger.setExchangeRate(calculation.exchangeRate());
        ledger.setTransferFee(calculation.transferFee());
        ledger.setFeeCurrency(calculation.feeCurrency());
        ledger.setTotalSenderDebit(calculation.totalSenderDebit());

        // 6. Perform AML screening.
        AmlScreeningService.AmlScreeningResult screeningResult =
                amlScreeningService.screenTransaction(request);

        if (screeningResult.hold()) {

            ledger.setStatus(
                    TransactionLedger.TransactionStatus.SUSPENDED_AML_HOLD
            );

            ledgerRepository.save(ledger);

            PaymentStatusHistory history = new PaymentStatusHistory(
                    uetr,
                    null,
                    TransactionLedger.TransactionStatus.SUSPENDED_AML_HOLD,
                    screeningResult.reason()
            );

            historyRepository.save(history);

            throw new AmlHoldException(
                    uetr,
                    screeningResult.reason()
            );
        }

        // 7. Use the route determined during financial calculation.
        String routeType = calculation.routeType();

        // 8. Mark the payment as accepted by our platform.
        ledger.setStatus(
                TransactionLedger.TransactionStatus.ACCEPTED
        );

        ledgerRepository.save(ledger);

        PaymentStatusHistory initialHistory = new PaymentStatusHistory(
                uetr,
                null,
                TransactionLedger.TransactionStatus.ACCEPTED,
                "Payment accepted after successful AML screening"
        );

        historyRepository.save(initialHistory);

        // 9. Find the adapter responsible for this route.
        PaymentNetworkAdapter adapter =
                networkAdapterRegistry.getAdapter(routeType);

        // 10. Submit the original request with the same calculated result.
        NetworkSubmissionResult submissionResult =
                adapter.submit(request, calculation, uetr);

        if (submissionResult == null) {
            throw new IllegalStateException(
                    "Network adapter returned no submission result."
            );
        }

        if (!routeType.equals(submissionResult.routeType())) {
            throw new IllegalStateException(
                    "Network adapter returned an unexpected route type."
            );
        }

        if (submissionResult.status() == null) {
            throw new IllegalStateException(
                    "Network adapter returned no submission status."
            );
        }

        // 11. Persist network submission evidence.
        NetworkSubmission networkSubmission = new NetworkSubmission(
                ledger,
                submissionResult.routeType(),
                submissionResult.networkReference(),
                NetworkSubmission.SubmissionStatus.valueOf(
                        submissionResult.status().name()
                ),
                submissionResult.submittedAt(),
                submissionResult.respondedAt(),
                submissionResult.responseMessage()
        );

        networkSubmissionRepository.save(networkSubmission);

        // 12. Persist the ISO message whenever the adapter generated one.
        String isoXmlMessage = submissionResult.isoXmlMessage();

        if (isoXmlMessage != null && !isoXmlMessage.isBlank()) {

            IsoMessage isoMessage = new IsoMessage();

            isoMessage.setTransactionLedger(ledger);
            isoMessage.setMessageType("pacs.008");
            isoMessage.setSchemaVersion("001.14");
            isoMessage.setMessageContent(isoXmlMessage);
            isoMessage.setValidationStatus("VALIDATED");

            isoMessageRepository.save(isoMessage);
        }

        // Cross-border submissions must contain their ISO message.
        if ("CROSS_BORDER_CORRESPONDENT".equals(routeType)
                && (isoXmlMessage == null || isoXmlMessage.isBlank())) {

            throw new IllegalStateException(
                    "Cross-border adapter did not return an ISO 20022 message."
            );
        }

        // 13. Handle all expected simulated network outcomes.
        switch (submissionResult.status()) {

            case SIMULATED_ACKNOWLEDGED -> {

                String reason =
                        "Simulated network acknowledged the payment instruction. "
                                + "Network reference: "
                                + submissionResult.networkReference();

                transactionStatusService.updateStatus(
                        uetr,
                        TransactionLedger.TransactionStatus.PROCESSING,
                        reason
                );

                System.out.println(
                        "Simulated network acknowledged payment. "
                                + "UETR: " + uetr
                );

                return new PaymentSagaResult(
                        PaymentSagaResult.Outcome.ACKNOWLEDGED,
                        "Payment accepted for simulated processing. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + submissionResult.networkReference()
                );
            }

            case SIMULATED_NACK -> {

                String reason =
                        "Simulated network rejected the payment instruction. "
                                + "Network reference: "
                                + submissionResult.networkReference();

                transactionStatusService.updateStatus(
                        uetr,
                        TransactionLedger.TransactionStatus.REJECTED,
                        reason
                );

                System.out.println(
                        "Simulated network rejected payment. "
                                + "UETR: " + uetr
                );

                return new PaymentSagaResult(
                        PaymentSagaResult.Outcome.REJECTED,
                        "Simulated network rejected the payment. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + submissionResult.networkReference()
                );
            }

            case SIMULATED_TIMEOUT -> {

                String reason =
                        "No simulated network response was received. "
                                + "Payment is awaiting confirmation. "
                                + "Network reference: "
                                + submissionResult.networkReference();

                transactionStatusService.updateStatus(
                        uetr,
                        TransactionLedger.TransactionStatus.NETWORK_PENDING,
                        reason
                );

                System.out.println(
                        "Simulated network timed out. "
                                + "Payment is NETWORK_PENDING. "
                                + "UETR: " + uetr
                );

                return new PaymentSagaResult(
                        PaymentSagaResult.Outcome.NETWORK_PENDING,
                        "No simulated network response was received. "
                                + "Payment is awaiting confirmation. "
                                + "UETR: " + uetr
                                + ". Network reference: "
                                + submissionResult.networkReference()
                );
            }
        }

        throw new IllegalStateException(
                "Unsupported simulated network submission status."
        );
    }
}
