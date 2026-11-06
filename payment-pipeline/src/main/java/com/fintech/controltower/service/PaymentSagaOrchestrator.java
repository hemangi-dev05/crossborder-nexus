
package com.fintech.controltower.service;

import com.fintech.controltower.domain.PaymentRequest;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;
import com.fintech.controltower.domain.AmlScreeningService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentSagaOrchestrator {

    private final TransactionLedgerRepository ledgerRepository;
    private final AmlScreeningService amlScreeningService;
    private final IsoMessageCompilerService isoCompilerService;

    private static final BigDecimal ROUTING_THRESHOLD =
            new BigDecimal("10000.00");

    public PaymentSagaOrchestrator(
            TransactionLedgerRepository ledgerRepository,
            AmlScreeningService amlScreeningService,
            IsoMessageCompilerService isoCompilerService) {

        this.ledgerRepository = ledgerRepository;
        this.amlScreeningService = amlScreeningService;
        this.isoCompilerService = isoCompilerService;
    }

    public String executePaymentSaga(PaymentRequest request) {

        // 1. Generate unique transaction reference
        String uetr = UUID.randomUUID().toString();

        // 2. Initialize transaction ledger record
        TransactionLedger ledger = new TransactionLedger();

        ledger.setUetr(uetr);
        ledger.setTransactionId(request.transactionId());

        // Sender details
        ledger.setDebtorName(request.debtorName());
        ledger.setDebtorAccountNumber(request.debtorAccountNumber());
        ledger.setDebtorBic(request.debtorBic());
        ledger.setDebtorClearingSystemCode(request.debtorClearingSystemCode());
        ledger.setDebtorClearingMemberId(request.debtorClearingMemberId());

        // Beneficiary details
        ledger.setCreditorName(request.creditorName());
        ledger.setCreditorAccountNumber(request.creditorAccountNumber());
        ledger.setCreditorBic(request.creditorBic());
        ledger.setCreditorClearingSystemCode(request.creditorClearingSystemCode());
        ledger.setCreditorClearingMemberId(request.creditorClearingMemberId());

        // Payment details
        ledger.setAmount(request.amount());
        ledger.setCurrency(request.currency());

        // Initial lifecycle status
        ledger.setStatus(TransactionLedger.TransactionStatus.ACCEPTED);

        // 3. Perform AML screening
        AmlScreeningService.AmlScreeningResult screeningResult =
                amlScreeningService.screenTransaction(request);

        if (screeningResult.hold()) {
            throw new SecurityException(
                    "Transaction blocked by AML compliance policy for UETR: "
                            + uetr + ". Reason: " + screeningResult.reason()
            );
        }

        // 4. Evaluate payment routing
        if (request.amount().compareTo(ROUTING_THRESHOLD) >= 0
                || !"INR".equalsIgnoreCase(request.currency())) {

            // Cross-border or high-value payment: generate ISO 20022 pacs.008
            String isoXmlMessage =
                    isoCompilerService.compilePacs008(request, uetr);

            System.out.println(
                    "Generated ISO 20022 pacs.008 message. UETR: " + uetr
            );

        } else {

            // Domestic payment simulation
            System.out.println(
                    "Selected domestic payment processing route. UETR: " + uetr
            );
        }

        // 5. Persist transaction record
        ledgerRepository.save(ledger);

        System.out.println(
                "Payment ledger saved successfully. Status: "
                        + ledger.getStatus() + ", UETR: " + uetr
        );

        return uetr;
    }
}
