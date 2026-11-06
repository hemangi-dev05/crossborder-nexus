package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.TransactionLedger;

public record StatusUpdateRequest(
        TransactionLedger.TransactionStatus status
) {}