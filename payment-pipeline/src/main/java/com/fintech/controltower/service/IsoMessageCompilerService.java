
package com.fintech.controltower.service;

import com.fintech.controltower.domain.PaymentRequest;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class IsoMessageCompilerService {

    private static final String NAMESPACE =
            "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14";

    // Project simulation policy: charges are shared.
    private static final String CHARGE_BEARER = "SHAR";

    private static final Pattern UETR_PATTERN = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"
    );

    private static final Pattern BIC_PATTERN = Pattern.compile(
            "[A-Z0-9]{8}([A-Z0-9]{3})?"
    );

    private final IsoMessageValidatorService isoMessageValidatorService;

    public IsoMessageCompilerService(
            IsoMessageValidatorService isoMessageValidatorService
    ) {
        this.isoMessageValidatorService = isoMessageValidatorService;
    }

    public String compilePacs008(PaymentRequest request, String uetr) {
        validate(request, uetr);

        String currency = request.currency()
                .trim()
                .toUpperCase(Locale.ROOT);

        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);

        String messageId = "MSG"
                + uetr.replace("-", "").substring(0, 20);

        try {
            StringWriter output = new StringWriter();

            XMLStreamWriter xml = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(output);

            xml.writeStartDocument("UTF-8", "1.0");

            // Root document
            start(xml, "Document");
            xml.writeDefaultNamespace(NAMESPACE);

            // Financial institution to financial institution transfer
            start(xml, "FIToFICstmrCdtTrf");

            writeGroupHeader(xml, messageId, timestamp);

            writeCreditTransferTransaction(
                    xml, request, uetr, currency
            );

            xml.writeEndElement(); // FIToFICstmrCdtTrf
            xml.writeEndElement(); // Document

            xml.writeEndDocument();
            xml.flush();
            xml.close();

            String generatedXml = output.toString();

            // Validate generated XML against the actual ISO 20022 XSD.
            isoMessageValidatorService.validate(generatedXml);

            return generatedXml;

        } catch (XMLStreamException e) {
            throw new IllegalStateException(
                    "Unable to generate ISO 20022 pacs.008 XML", e
            );
        }
    }

    private void writeGroupHeader(
            XMLStreamWriter xml,
            String messageId,
            String timestamp
    ) throws XMLStreamException {

        start(xml, "GrpHdr");

        writeElement(xml, "MsgId", messageId);
        writeElement(xml, "CreDtTm", timestamp);
        writeElement(xml, "NbOfTxs", "1");

        start(xml, "SttlmInf");
        writeElement(xml, "SttlmMtd", "CLRG");
        xml.writeEndElement(); // SttlmInf

        xml.writeEndElement(); // GrpHdr
    }

    private void writeCreditTransferTransaction(
            XMLStreamWriter xml,
            PaymentRequest request,
            String uetr,
            String currency
    ) throws XMLStreamException {

        start(xml, "CdtTrfTxInf");

        // 1. Payment identification
        start(xml, "PmtId");
        writeElement(xml, "EndToEndId", request.transactionId().trim());
        writeElement(xml, "UETR", uetr);
        xml.writeEndElement(); // PmtId

        // 2. Interbank settlement amount
        start(xml, "IntrBkSttlmAmt");
        xml.writeAttribute("Ccy", currency);
        xml.writeCharacters(request.amount().toPlainString());
        xml.writeEndElement();

        // 3. Settlement date for simulation
        writeElement(
                xml,
                "IntrBkSttlmDt",
                LocalDate.now(ZoneOffset.UTC).toString()
        );

        // 4. Charge bearer
        writeElement(xml, "ChrgBr", CHARGE_BEARER);

        // 5. Debtor
        start(xml, "Dbtr");
        writeElement(xml, "Nm", request.debtorName().trim());
        xml.writeEndElement();

        // 6. Debtor account
        writeAccount(
                xml,
                "DbtrAcct",
                request.debtorAccountNumber()
        );

        // 7. Debtor's financial institution
        writeAgent(
                xml,
                "DbtrAgt",
                request.debtorBic(),
                request.debtorClearingSystemCode(),
                request.debtorClearingMemberId()
        );

        // 8. Creditor's financial institution
        writeAgent(
                xml,
                "CdtrAgt",
                request.creditorBic(),
                request.creditorClearingSystemCode(),
                request.creditorClearingMemberId()
        );

        // 9. Creditor
        start(xml, "Cdtr");
        writeElement(xml, "Nm", request.creditorName().trim());
        xml.writeEndElement();

        // 10. Creditor account
        writeAccount(
                xml,
                "CdtrAcct",
                request.creditorAccountNumber()
        );

        xml.writeEndElement(); // CdtTrfTxInf
    }

    private void writeAccount(
            XMLStreamWriter xml,
            String elementName,
            String accountNumber
    ) throws XMLStreamException {

        start(xml, elementName);

        start(xml, "Id");

        start(xml, "Othr");
        writeElement(xml, "Id", accountNumber.trim());
        xml.writeEndElement(); // Othr

        xml.writeEndElement(); // Id
        xml.writeEndElement(); // Account
    }

    private void writeAgent(
            XMLStreamWriter xml,
            String elementName,
            String bic,
            String clearingSystemCode,
            String clearingMemberId
    ) throws XMLStreamException {

        start(xml, elementName);

        start(xml, "FinInstnId");

        if (bic != null && !bic.isBlank()) {
            writeElement(
                    xml,
                    "BICFI",
                    bic.trim().toUpperCase(Locale.ROOT)
            );
        }

        if (clearingMemberId != null && !clearingMemberId.isBlank()) {

            start(xml, "ClrSysMmbId");

            if (clearingSystemCode != null
                    && !clearingSystemCode.isBlank()) {

                start(xml, "ClrSysId");

                writeElement(
                        xml,
                        "Prtry",
                        clearingSystemCode.trim()
                );

                xml.writeEndElement(); // ClrSysId
            }

            writeElement(
                    xml,
                    "MmbId",
                    clearingMemberId.trim()
            );

            xml.writeEndElement(); // ClrSysMmbId
        }

        xml.writeEndElement(); // FinInstnId
        xml.writeEndElement(); // Agent
    }

    private static void start(
            XMLStreamWriter xml,
            String element
    ) throws XMLStreamException {

        xml.writeStartElement("", element, NAMESPACE);
    }

    private static void writeElement(
            XMLStreamWriter xml,
            String element,
            String value
    ) throws XMLStreamException {

        start(xml, element);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static void validate(
            PaymentRequest request,
            String uetr
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "PaymentRequest must not be null"
            );
        }

        if (uetr == null || !UETR_PATTERN.matcher(uetr).matches()) {
            throw new IllegalArgumentException(
                    "UETR must be a valid lowercase UUIDv4"
            );
        }

        requireText(request.transactionId(), "Transaction ID", 35);
        requireText(request.debtorName(), "Debtor name", 140);
        requireText(request.creditorName(), "Creditor name", 140);

        requireText(
                request.debtorAccountNumber(),
                "Debtor account number",
                34
        );

        requireText(
                request.creditorAccountNumber(),
                "Creditor account number",
                34
        );

        validateAgent(
                request.debtorBic(),
                request.debtorClearingSystemCode(),
                request.debtorClearingMemberId(),
                "Debtor"
        );

        validateAgent(
                request.creditorBic(),
                request.creditorClearingSystemCode(),
                request.creditorClearingMemberId(),
                "Creditor"
        );

        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        BigDecimal normalizedAmount =
                request.amount().stripTrailingZeros();

        if (normalizedAmount.precision() > 18
                || normalizedAmount.scale() > 5) {

            throw new IllegalArgumentException(
                    "Amount exceeds ISO 20022 precision limits"
            );
        }

        if (request.currency() == null
                || !request.currency().trim().matches("[A-Za-z]{3}")) {

            throw new IllegalArgumentException(
                    "Currency must contain exactly three letters"
            );
        }
    }

    private static void validateAgent(
            String bic,
            String clearingSystemCode,
            String clearingMemberId,
            String party
    ) {

        boolean hasBic = bic != null && !bic.isBlank();

        boolean hasMemberId = clearingMemberId != null
                && !clearingMemberId.isBlank();

        if (!hasBic && !hasMemberId) {
            throw new IllegalArgumentException(
                    party + " bank must have a BIC or clearing member ID"
            );
        }

        if (hasBic && !BIC_PATTERN.matcher(
                bic.trim().toUpperCase(Locale.ROOT)
        ).matches()) {

            throw new IllegalArgumentException(
                    party + " BIC must contain 8 or 11 alphanumeric characters"
            );
        }

        if (clearingSystemCode != null
                && !clearingSystemCode.isBlank()
                && !hasMemberId) {

            throw new IllegalArgumentException(
                    party + " clearing system code requires a clearing member ID"
            );
        }

        if (hasMemberId) {
            requireText(
                    clearingMemberId,
                    party + " clearing member ID",
                    35
            );
        }

        if (clearingSystemCode != null
                && !clearingSystemCode.isBlank()) {

            requireText(
                    clearingSystemCode,
                    party + " clearing system code",
                    35
            );
        }
    }

    private static void requireText(
            String value,
            String field,
            int maxLength
    ) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed "
                            + maxLength + " characters"
            );
        }
    }
}
