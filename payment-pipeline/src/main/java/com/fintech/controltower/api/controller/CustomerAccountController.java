
package com.fintech.controltower.api.controller;

import com.fintech.controltower.api.dto.CustomerAccountResponse;
import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class CustomerAccountController {

    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;

    public CustomerAccountController(
            CustomerRepository customerRepository,
            CustomerAccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    // Get all accounts belonging to the authenticated customer
    @GetMapping("/me")
    public ResponseEntity<List<CustomerAccountResponse>> getMyAccounts(
            Authentication authentication) {

        Customer customer = findAuthenticatedCustomer(authentication);

        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<CustomerAccountResponse> accounts = accountRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(accounts);
    }

    // Get one account belonging to the authenticated customer
    @GetMapping("/me/{accountId}")
    public ResponseEntity<CustomerAccountResponse> getMyAccount(
            @PathVariable Long accountId,
            Authentication authentication) {

        Customer customer = findAuthenticatedCustomer(authentication);

        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return accountRepository
                .findByIdAndCustomer_Id(accountId, customer.getId())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Customer findAuthenticatedCustomer(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return null;
        }

        return customerRepository
                .findByEmail(authentication.getName())
                .orElse(null);
    }

    private CustomerAccountResponse toResponse(
            CustomerAccount account) {

        return new CustomerAccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountHolderName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getCurrentBalance(),
                account.getReservedBalance(),
                account.getAvailableBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
