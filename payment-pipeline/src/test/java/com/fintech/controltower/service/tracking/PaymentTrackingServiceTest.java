
package com.fintech.controltower.service.tracking;

import com.fintech.controltower.api.dto.PaymentTrackingResponse;
import com.fintech.controltower.domain.entity.NetworkResponse;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.repository.IsoMessageRepository;
import com.fintech.controltower.domain.repository.NetworkResponseRepository;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.repository.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentTrackingServiceTest {

    private TransactionLedgerRepository ledgerRepository;
    private PaymentStatusHistoryRepository historyRepository;
    private IsoMessageRepository isoMessageRepository;
    private NetworkSubmissionRepository submissionRepository;
    private NetworkResponseRepository responseRepository;

    private PaymentTrackingService trackingService;

    private static final String UETR =
            "364d7a6f-5144-4de7-b66c-42227a947ba1";

    @BeforeEach
    void setUp() {

        ledgerRepository = mock(TransactionLedgerRepository.class);
        historyRepository = mock(PaymentStatusHistoryRepository.class);
        isoMessageRepository = mock(IsoMessageRepository.class);
        submissionRepository = mock(NetworkSubmissionRepository.class);
        responseRepository = mock(NetworkResponseRepository.class);

        trackingService = new PaymentTrackingService(
                ledgerRepository,
                historyRepository,
                isoMessageRepository,
                submissionRepository,
                responseRepository
        );
    }

    @Test
    void shouldIncludeDelayedResponseAlongsideOriginalTimeout() {

        TransactionLedger ledger = mock(TransactionLedger.class);

        when(ledger.getUetr()).thenReturn(UETR);

        when(ledgerRepository.findById(UETR))
                .thenReturn(Optional.of(ledger));

        NetworkSubmission timedOutSubmission =
                mock(NetworkSubmission.class);

        when(timedOutSubmission.getId()).thenReturn(10L);
        when(timedOutSubmission.getStatus())
                .thenReturn(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);
        when(timedOutSubmission.getResponseMessage())
                .thenReturn("Original network request timed out");

        NetworkSubmission normalSubmission =
                mock(NetworkSubmission.class);

        when(normalSubmission.getId()).thenReturn(11L);
        when(normalSubmission.getStatus())
                .thenReturn(NetworkSubmission.SubmissionStatus.SIMULATED_ACKNOWLEDGED);

        when(submissionRepository
                .findByTransactionLedger_UetrOrderBySubmittedAtAsc(UETR))
                .thenReturn(List.of(timedOutSubmission, normalSubmission));

        NetworkResponse delayedResponse = mock(NetworkResponse.class);

        when(delayedResponse.getStatus())
                .thenReturn(NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED);

        when(delayedResponse.getResponseMessage())
                .thenReturn("Delayed ACK received from correspondent bank");

        when(delayedResponse.getNetworkSubmission())
                .thenReturn(timedOutSubmission);

        when(responseRepository.findByNetworkSubmission_IdIn(
                List.of(10L, 11L)))
                .thenReturn(List.of(delayedResponse));

        when(historyRepository.findByUetrOrderByChangedAtAsc(UETR))
                .thenReturn(List.of());

        when(isoMessageRepository
                .findByTransactionLedger_UetrOrderByCreatedAtAsc(UETR))
                .thenReturn(List.of());

        PaymentTrackingResponse result =
                trackingService.getPaymentTracking(UETR);

        assertThat(result.networkSubmissions()).hasSize(2);

        var timedOut = result.networkSubmissions().get(0);

        assertThat(timedOut.status())
                .isEqualTo(NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT);

        assertThat(timedOut.responseMessage())
                .isEqualTo("Original network request timed out");

        assertThat(timedOut.delayedResponse()).isNotNull();

        assertThat(timedOut.delayedResponse().status())
                .isEqualTo(NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED);

        assertThat(timedOut.delayedResponse().responseMessage())
                .isEqualTo("Delayed ACK received from correspondent bank");

        var normal = result.networkSubmissions().get(1);

        assertThat(normal.delayedResponse()).isNull();

        verify(responseRepository, times(1))
                .findByNetworkSubmission_IdIn(List.of(10L, 11L));
    }
}
