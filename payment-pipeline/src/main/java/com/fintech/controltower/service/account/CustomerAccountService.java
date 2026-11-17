
package com.fintech.controltower.service.account;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class CustomerAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 10;

    private final CustomerAccountRepository accountRepository;

    public CustomerAccountService(
            CustomerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public CustomerAccount createInitialSavingsAccount(Customer customer) {

        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted customer is required to create an account"
            );
        }

        String accountNumber = generateUniqueAccountNumber();

        CustomerAccount account = new CustomerAccount(
                accountNumber,
                customer,
                customer.getFullName(),
                CustomerAccount.AccountType.SAVINGS,
                "INR"
        );

        return accountRepository.saveAndFlush(account);
    }

    private String generateUniqueAccountNumber() {

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            long number = 100_000_000_000L
                    + RANDOM.nextLong(900_000_000_000L);

            String accountNumber = String.valueOf(number);

            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique account number"
        );
    }
}
