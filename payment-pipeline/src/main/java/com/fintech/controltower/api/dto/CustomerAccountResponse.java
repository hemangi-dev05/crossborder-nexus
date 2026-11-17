
package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.CustomerAccount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerAccountResponse(
        Long id,
        String accountNumber,
        String accountHolderName,
        CustomerAccount.AccountType accountType,
        String currency,
        BigDecimal currentBalance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance,
        CustomerAccount.AccountStatus status,
        LocalDateTime createdAt
) {
}
