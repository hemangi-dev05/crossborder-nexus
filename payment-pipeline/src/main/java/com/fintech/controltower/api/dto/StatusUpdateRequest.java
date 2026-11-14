package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.TransactionLedger;

public record StatusUpdateRequest(
        TransactionLedger.TransactionStatus status
) {}