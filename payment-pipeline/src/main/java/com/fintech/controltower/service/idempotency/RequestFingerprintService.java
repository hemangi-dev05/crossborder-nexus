
package com.fintech.controltower.service.idempotency;

import com.fintech.controltower.domain.model.PaymentRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class RequestFingerprintService {

    public String generateFingerprint(PaymentRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Payment request cannot be null"
            );
        }

        try {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream();

            DataOutputStream dataStream =
                    new DataOutputStream(byteStream);

            // Debtor details
            writeField(dataStream, request.debtorName());
            writeField(dataStream, request.debtorAccountNumber());
            writeField(dataStream, normalize(request.debtorBic()));
            writeField(dataStream, normalize(request.debtorCountryCode()));
            writeField(dataStream, request.debtorClearingSystemCode());
            writeField(dataStream, request.debtorClearingMemberId());

            // Creditor details
            writeField(dataStream, request.creditorName());
            writeField(dataStream, request.creditorAccountNumber());
            writeField(dataStream, normalize(request.creditorBic()));
            writeField(dataStream, normalize(request.creditorCountryCode()));
            writeField(dataStream, request.creditorClearingSystemCode());
            writeField(dataStream, request.creditorClearingMemberId());

            // Financial instruction
            writeField(dataStream, normalize(request.sourceCurrency()));
            writeField(dataStream, normalize(request.destinationCurrency()));

            writeField(
                    dataStream,
                    request.amountMode() == null
                            ? null
                            : request.amountMode().name()
            );

            // Normalize amount so 100.0 and 100.00
            // produce the same fingerprint.
            String normalizedAmount = request.enteredAmount() == null
                    ? null
                    : request.enteredAmount()
                    .stripTrailingZeros()
                    .toPlainString();

            writeField(dataStream, normalizedAmount);

            dataStream.flush();

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(byteStream.toByteArray());

            return HexFormat.of().formatHex(hash);

        } catch (IOException | NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "Unable to generate payment request fingerprint",
                    e
            );
        }
    }

    private String normalize(String value) {
        return value == null
                ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private void writeField(
            DataOutputStream dataStream,
            String value
    ) throws IOException {

        if (value == null) {
            dataStream.writeInt(-1);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        dataStream.writeInt(bytes.length);
        dataStream.write(bytes);
    }
}
