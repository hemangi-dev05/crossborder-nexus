
package com.fintech.controltower.service;

import com.fintech.controltower.domain.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoMessageCompilerServiceTest {

    private IsoMessageCompilerService compiler;
    private IsoMessageValidatorService validator;

    private static final String VALID_UETR =
            "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        validator = new IsoMessageValidatorService();
        compiler = new IsoMessageCompilerService(validator);
    }

    private PaymentRequest createValidPayment() {
        return new PaymentRequest(
                "TXN-2026-001",
                "Hemangi Sharma",
                "123456789012",
                "DEUTDEFF",
                "DE",
                null,
                null,
                "John Smith",
                "987654321098",
                "CHASUS33",
                "US",
                null,
                null,
                new BigDecimal("1250.50"),
                "USD"
        );
    }

    @Test
    void shouldGenerateValidPacs008Xml() {

        String xml = compiler.compilePacs008(
                createValidPayment(),
                VALID_UETR
        );

        assertThat(xml).isNotBlank();
        assertThat(xml).contains("Document");
        assertThat(xml).contains("FIToFICstmrCdtTrf");
        assertThat(xml).contains(VALID_UETR);
        assertThat(xml).contains("1250.50");

        validator.validate(xml);
    }

    @Test
    void shouldRejectInvalidUetr() {

        assertThatThrownBy(() ->
                compiler.compilePacs008(
                        createValidPayment(),
                        "invalid-uetr"
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UETR");
    }

    @Test
    void shouldRejectZeroAmount() {

        PaymentRequest payment = new PaymentRequest(
                "TXN-2026-002",
                "Hemangi Sharma",
                "123456789012",
                "DEUTDEFF",
                "DE",
                null,
                null,
                "John Smith",
                "987654321098",
                "CHASUS33",
                "US",
                null,
                null,
                BigDecimal.ZERO,
                "USD"
        );

        assertThatThrownBy(() ->
                compiler.compilePacs008(payment, VALID_UETR)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount");
    }

    @Test
    void shouldRejectAgentWithoutBicOrClearingMemberId() {

        PaymentRequest payment = new PaymentRequest(
                "TXN-2026-003",
                "Hemangi Sharma",
                "123456789012",
                null,
                "DE",
                null,
                null,
                "John Smith",
                "987654321098",
                "CHASUS33",
                "US",
                null,
                null,
                new BigDecimal("500.00"),
                "USD"
        );

        assertThatThrownBy(() ->
                compiler.compilePacs008(payment, VALID_UETR)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debtor bank");
    }

    @Test
    void shouldRejectInvalidXml() {

        String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <InvalidDocument>
                    <InvalidElement>Test</InvalidElement>
                </InvalidDocument>
                """;

        assertThatThrownBy(() ->
                validator.validate(invalidXml)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("XSD validation");
    }

    @Test
    void shouldRejectInvalidBic() {

        PaymentRequest payment = new PaymentRequest(
                "TXN-2026-004",
                "Hemangi Sharma",
                "123456789012",
                "DEUT12FF",
                "DE",
                null,
                null,
                "John Smith",
                "987654321098",
                "CHASUS33",
                "US",
                null,
                null,
                new BigDecimal("500.00"),
                "USD"
        );

        assertThatThrownBy(() ->
                compiler.compilePacs008(payment, VALID_UETR)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BIC");
    }
}
