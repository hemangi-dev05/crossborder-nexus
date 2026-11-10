
package com.fintech.controltower.service;

import com.fintech.controltower.domain.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Native sanctions / AML name-screening gateway.
 * Blocked names are configurable via aml.blocked-names (comma separated).
 */
@Service
public class AmlScreeningService {

    private static final Logger log = LoggerFactory.getLogger(AmlScreeningService.class);

    /** Outcome of a screening run. */
    public record AmlScreeningResult(boolean hold, String reason) {
        public static AmlScreeningResult clear() {
            return new AmlScreeningResult(false, null);
        }

        public static AmlScreeningResult blocked(String reason) {
            return new AmlScreeningResult(true, reason);
        }
    }

    private final List<String> blockedNames;

    public AmlScreeningService(
            @Value("${aml.blocked-names:Satoshi Nakamoto}") String blockedNamesProperty) {
        this.blockedNames = Arrays.stream(blockedNamesProperty.split(","))
                .map(AmlScreeningService::normalize)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    public AmlScreeningResult screenTransaction(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request must not be null for AML screening");
        }

        Optional<String> debtorHit = match(request.debtorName());
        if (debtorHit.isPresent()) {
            log.warn("AML sanctions match on debtor for transactionId={}", request.transactionId());
            return AmlScreeningResult.blocked(
                    "Debtor matches sanctions list entry: " + debtorHit.get());
        }

        Optional<String> creditorHit = match(request.creditorName());
        if (creditorHit.isPresent()) {
            log.warn("AML sanctions match on creditor for transactionId={}", request.transactionId());
            return AmlScreeningResult.blocked(
                    "Creditor matches sanctions list entry: " + creditorHit.get());
        }

        return AmlScreeningResult.clear();
    }

    private Optional<String> match(String partyName) {
        String normalizedParty = normalize(partyName);
        if (normalizedParty.isEmpty()) {
            return Optional.empty();
        }

        String padded = " " + normalizedParty + " ";

        return blockedNames.stream()
                .filter(blocked -> padded.contains(" " + blocked + " "))
                .findFirst();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
