
package com.fintech.controltower.service.payment;

import com.fintech.controltower.service.aml.AmlScreeningService;

import com.fintech.controltower.domain.entity.IsoMessage;
import com.fintech.controltower.domain.repository.IsoMessageRepository;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.domain.entity.PaymentStatusHistory;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;
import com.fintech.controltower.exception.AmlHoldException;
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
    private final PaymentRoutingService paymentRoutingService;
    private final NetworkAdapterRegistry networkAdapterRegistry;
    private final TransactionStatusService transactionStatusService;

    public PaymentSagaOrchestrator(
            TransactionLedgerRepository ledgerRepository,
            PaymentStatusHistoryRepository historyRepository,
            IsoMessageRepository isoMessageRepository,
            NetworkSubmissionRepository networkSubmissionRepository,
            AmlScreeningService amlScreeningService,
            PaymentRoutingService paymentRoutingService,
            NetworkAdapterRegistry networkAdapterRegistry,
            TransactionStatusService transactionStatusService
    ) {
        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
        this.isoMessageRepository = isoMessageRepository;
        this.networkSubmissionRepository = networkSubmissionRepository;
        this.amlScreeningService = amlScreeningService;
        this.paymentRoutingService = paymentRoutingService;
        this.networkAdapterRegistry = networkAdapterRegistry;
        this.transactionStatusService = transactionStatusService;
    }

    @Transactional(noRollbackFor = SecurityException.class)
    public PaymentSagaResult executePaymentSaga(
            PaymentRequest request,
            String uetr
    ) {

        // 1. Initialize the transaction ledger.
        TransactionLedger ledger = new TransactionLedger();

        ledger.setUetr(uetr);
        ledger.setTransactionId(request.transactionId());

        // 2. Map sender details.
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

        // 3. Map beneficiary details.
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

        // 4. Map payment details.
        ledger.setAmount(request.amount());
        ledger.setCurrency(request.currency());

        // 5. Perform AML screening.
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

        // 6. Determine the configured payment route.
        String routeType = paymentRoutingService.determineRoute(
                request.debtorCountryCode(),
                request.creditorCountryCode(),
                request.currency()
        );

        // 7. Mark the payment as accepted by our platform.
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

        // 8. Find the adapter responsible for this route.
        PaymentNetworkAdapter adapter =
                networkAdapterRegistry.getAdapter(routeType);

        // 9. Submit the payment to the simulated network.
        NetworkSubmissionResult submissionResult =
                adapter.submit(request, uetr);

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

        // 10. Persist network submission evidence.
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

        // 11. Persist the ISO message whenever the adapter generated one.
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

        // 12. Handle all expected simulated network outcomes.
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
