
package com.fintech.controltower.service.security;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerRole;
import com.fintech.controltower.domain.repository.CustomerRepository;
import com.fintech.controltower.service.account.CustomerAccountService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final CustomerAccountService customerAccountService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            CustomerRepository customerRepository,
            CustomerAccountService customerAccountService,
            PasswordEncoder passwordEncoder
    ) {
        this.customerRepository = customerRepository;
        this.customerAccountService = customerAccountService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Customer register(
            String fullName,
            String email,
            String password,
            String phoneNumber,
            String countryCode,
            String city
    ) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters and be no more than 72 UTF-8 bytes"
            );
        }

        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code is required");
        }

        String normalizedName = fullName.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedCountryCode =
                countryCode.trim().toUpperCase(Locale.ROOT);

        String normalizedPhone = phoneNumber == null
                || phoneNumber.isBlank()
                ? null
                : phoneNumber.trim();

        String normalizedCity = city == null || city.isBlank()
                ? null
                : city.trim();

        if (normalizedName.length() > 150
                || normalizedEmail.length() > 254
                || !normalizedEmail.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
        )) {
            throw new IllegalArgumentException(
                    "Invalid name or email address"
            );
        }

        if (!normalizedCountryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "Country code must be a two-letter ISO code, such as IN."
            );
        }

        if (normalizedPhone != null
                && !normalizedPhone.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException(
                    "Phone number must use international format, such as +919876543210."
            );
        }

        if (normalizedCity != null && normalizedCity.length() > 100) {
            throw new IllegalArgumentException(
                    "City cannot exceed 100 characters."
            );
        }

        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException(
                    "Email is already registered"
            );
        }

        Customer customer = new Customer(
                normalizedName,
                normalizedEmail,
                passwordEncoder.encode(password),
                normalizedPhone,
                normalizedCountryCode,
                normalizedCity,
                CustomerRole.CUSTOMER
        );

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        // Joins this registration transaction.
        // If account creation fails, customer registration rolls back.
        customerAccountService.createInitialSavingsAccount(savedCustomer);

        return savedCustomer;
    }
}
