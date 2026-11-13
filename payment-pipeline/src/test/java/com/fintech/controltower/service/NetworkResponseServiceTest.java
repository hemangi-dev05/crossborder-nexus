
package com.fintech.controltower.service;

import com.fintech.controltower.domain.NetworkResponse;
import com.fintech.controltower.domain.NetworkResponseRepository;
import com.fintech.controltower.domain.NetworkSubmission;
import com.fintech.controltower.domain.NetworkSubmissionRepository;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NetworkResponseServiceTest {

    private NetworkSubmissionRepository submissionRepository;
    private NetworkResponseRepository responseRepository;
    private TransactionLedgerRepository ledgerRepository;
    private TransactionStatusService statusService;

    private NetworkResponseService networkResponseService;

    private NetworkSubmission submission;
    private TransactionLedger payment;
    private TransactionLedger paymentReference;

    private static final Long SUBMISSION_ID = 8L;
    private static final String UETR =
            "364d7a6f-5144-4de7-b66c-42227a947ba1";

    private static final String ACK_MESSAGE =
            "Delayed ACK received from correspondent bank";

    @BeforeEach
    void setUp() {

        submissionRepository = mock(NetworkSubmissionRepository.class);
        responseRepository = mock(NetworkResponseRepository.class);
        ledgerRepository = mock(TransactionLedgerRepository.class);
        statusService = mock(TransactionStatusService.class);

        networkResponseService = new NetworkResponseService(
                submissionRepository,
                responseRepository,
                ledgerRepository,
                statusService
        );

        submission = mock(NetworkSubmission.class);
        payment = mock(TransactionLedger.class);
        paymentReference = mock(TransactionLedger.class);

        when(submissionRepository.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission));

        when(submission.getStatus())
                .thenReturn(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);

        when(submission.getTransactionLedger())
                .thenReturn(paymentReference);

        when(paymentReference.getUetr()).thenReturn(UETR);

        when(ledgerRepository.findByUetrForUpdate(UETR))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus())
                .thenReturn(TransactionLedger.TransactionStatus.NETWORK_PENDING);

        when(responseRepository.findByNetworkSubmission_Id(SUBMISSION_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldProcessDelayedAcknowledgement() {

        when(responseRepository.save(any(NetworkResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        ACK_MESSAGE
                );

        assertThat(response.getStatus())
                .isEqualTo(NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED);

        assertThat(response.getResponseMessage()).isEqualTo(ACK_MESSAGE);
        assertThat(response.getReceivedAt()).isNotNull();

        verify(statusService).updateStatus(
                eq(UETR),
                eq(TransactionLedger.TransactionStatus.PROCESSING),
                contains("Delayed network ACK")
        );
    }

    @Test
    void shouldProcessDelayedRejection() {

        when(responseRepository.save(any(NetworkResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_NACK,
                        "Payment rejected by correspondent bank"
                );

        assertThat(response.getStatus())
                .isEqualTo(NetworkResponse.ResponseStatus.SIMULATED_NACK);

        verify(statusService).updateStatus(
                eq(UETR),
                eq(TransactionLedger.TransactionStatus.REJECTED),
                contains("Delayed network NACK")
        );
    }

    @Test
    void shouldReturnExistingResponseForDuplicateAcknowledgement() {

        NetworkResponse existingResponse = mock(NetworkResponse.class);

        when(existingResponse.getStatus())
                .thenReturn(NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED);

        when(existingResponse.getResponseMessage())
                .thenReturn(ACK_MESSAGE);

        when(responseRepository.findByNetworkSubmission_Id(SUBMISSION_ID))
                .thenReturn(Optional.of(existingResponse));

        NetworkResponse response =
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        ACK_MESSAGE
                );

        assertThat(response).isSameAs(existingResponse);

        verify(responseRepository, never()).save(any(NetworkResponse.class));
        verify(statusService, never()).updateStatus(
                anyString(),
                any(TransactionLedger.TransactionStatus.class),
                anyString()
        );
    }

    @Test
    void shouldRejectConflictingDelayedResponse() {

        NetworkResponse existingResponse = mock(NetworkResponse.class);

        when(existingResponse.getStatus())
                .thenReturn(NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED);

        when(existingResponse.getResponseMessage())
                .thenReturn(ACK_MESSAGE);

        when(responseRepository.findByNetworkSubmission_Id(SUBMISSION_ID))
                .thenReturn(Optional.of(existingResponse));

        assertThatThrownBy(() ->
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_NACK,
                        "Payment rejected by correspondent bank"
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different delayed response");

        verify(responseRepository, never()).save(any(NetworkResponse.class));
        verify(statusService, never()).updateStatus(
                anyString(),
                any(TransactionLedger.TransactionStatus.class),
                anyString()
        );
    }

    @Test
    void shouldRejectDelayedResponseForNonTimeoutSubmission() {

        when(submission.getStatus())
                .thenReturn(NetworkSubmission.SubmissionStatus.SIMULATED_ACKNOWLEDGED);

        assertThatThrownBy(() ->
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        ACK_MESSAGE
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed-out submissions");

        verify(responseRepository, never()).save(any(NetworkResponse.class));
        verify(statusService, never()).updateStatus(
                anyString(),
                any(TransactionLedger.TransactionStatus.class),
                anyString()
        );
    }

    @Test
    void shouldRejectUnknownSubmission() {

        when(submissionRepository.findById(SUBMISSION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                networkResponseService.processDelayedResponse(
                        SUBMISSION_ID,
                        NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED,
                        ACK_MESSAGE
                )
        ).isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Network submission not found");
    }
}
