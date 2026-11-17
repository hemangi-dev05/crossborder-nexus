
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

    private final CustomerAccountRepository accountRepository;

    public CustomerAccountService(
            CustomerAccountRepository accountRepository
    ) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public CustomerAccount createInitialSavingsAccount(Customer customer) {

        String accountNumber = generateAccountNumber();

        CustomerAccount account = new CustomerAccount(
                accountNumber,
                customer,
                customer.getFullName(),
                CustomerAccount.AccountType.SAVINGS,
                "INR"
        );

        return accountRepository.saveAndFlush(account);
    }

    private String generateAccountNumber() {

        for (int attempt = 0; attempt < 10; attempt++) {

            long number =
                    100_000_000_000L + RANDOM.nextLong(900_000_000_000L);

            String accountNumber = Long.toString(number);

            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique account number."
        );
    }
}
