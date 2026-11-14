
package com.fintech.controltower.service.iso20022;

import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.financial.model.AmountMode;
import com.fintech.controltower.financial.model.PaymentCalculationResult;
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
                "USD",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1250.50")
        );
    }

    private PaymentCalculationResult createValidCalculation() {
        return new PaymentCalculationResult(
                AmountMode.SOURCE_AMOUNT,
                "CROSS_BORDER_CORRESPONDENT",
                "USD",
                "USD",
                new BigDecimal("1250.50"),
                new BigDecimal("1250.50"),
                BigDecimal.ONE,
                new BigDecimal("5.00"),
                "USD",
                new BigDecimal("1255.50")
        );
    }

    @Test
    void shouldGenerateValidPacs008Xml() {

        String xml = compiler.compilePacs008(
                createValidPayment(),
                createValidCalculation(),
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
    void shouldUseCalculatedDestinationAmountAndCurrency() {

        PaymentCalculationResult calculation =
                new PaymentCalculationResult(
                        AmountMode.SOURCE_AMOUNT,
                        "CROSS_BORDER_CORRESPONDENT",
                        "INR",
                        "USD",
                        new BigDecimal("85000.00"),
                        new BigDecimal("1003.54"),
                        new BigDecimal("84.70"),
                        new BigDecimal("250.00"),
                        "INR",
                        new BigDecimal("85250.00")
                );

        String xml = compiler.compilePacs008(
                createValidPayment(),
                calculation,
                VALID_UETR
        );

        assertThat(xml).contains("Ccy=\"USD\"");
        assertThat(xml).contains("1003.54");
        assertThat(xml).doesNotContain("85000.00");
    }

    @Test
    void shouldRejectInvalidUetr() {

        assertThatThrownBy(() ->
                compiler.compilePacs008(
                        createValidPayment(),
                        createValidCalculation(),
                        "invalid-uetr"
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UETR");
    }

    @Test
    void shouldRejectZeroDestinationAmount() {

        PaymentCalculationResult calculation =
                new PaymentCalculationResult(
                        AmountMode.SOURCE_AMOUNT,
                        "CROSS_BORDER_CORRESPONDENT",
                        "USD",
                        "USD",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "USD",
                        BigDecimal.ZERO
                );

        assertThatThrownBy(() ->
                compiler.compilePacs008(
                        createValidPayment(),
                        calculation,
                        VALID_UETR
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination amount");
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
                "USD",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("500.00")
        );

        assertThatThrownBy(() ->
                compiler.compilePacs008(
                        payment,
                        createValidCalculation(),
                        VALID_UETR
                )
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
                "USD",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("500.00")
        );

        assertThatThrownBy(() ->
                compiler.compilePacs008(
                        payment,
                        createValidCalculation(),
                        VALID_UETR
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BIC");
    }
}
