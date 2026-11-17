
package com.fintech.controltower.api.controller;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.entity.CustomerRole;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;
import com.fintech.controltower.service.account.CustomerAccountService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerAccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

    @Autowired
    private CustomerAccountService accountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void shouldAllowCustomerToAccessOnlyTheirOwnAccounts()
            throws Exception {

        // Create two customers
        Customer firstCustomer = createCustomer(
                "Customer One",
                "one@test.com"
        );

        Customer secondCustomer = createCustomer(
                "Customer Two",
                "two@test.com"
        );

        // Create an account for each customer
        CustomerAccount firstAccount =
                accountService.createInitialSavingsAccount(firstCustomer);

        CustomerAccount secondAccount =
                accountService.createInitialSavingsAccount(secondCustomer);

        // Authenticate as the first customer
        String loginRequest = """
                {
                    "email": "one@test.com",
                    "password": "SecurePass123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();

        // Verify first customer can list their own accounts
        mockMvc.perform(
                        get("/api/v1/accounts/me")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber")
                        .value(firstAccount.getAccountNumber()));

        // Verify first customer can access their own account
        mockMvc.perform(
                        get("/api/v1/accounts/me/{accountId}",
                                firstAccount.getId())
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber")
                        .value(firstAccount.getAccountNumber()))
                .andExpect(jsonPath("$.availableBalance")
                        .value(0.00));

        // Verify first customer cannot access another customer's account
        mockMvc.perform(
                        get("/api/v1/accounts/me/{accountId}",
                                secondAccount.getId())
                                .session(session)
                )
                .andExpect(status().isNotFound());
    }

    private Customer createCustomer(String name, String email) {

        Customer customer = new Customer(
                name,
                email,
                passwordEncoder.encode("SecurePass123"),
                null,
                "IN",
                null,
                CustomerRole.CUSTOMER
        );

        return customerRepository.saveAndFlush(customer);
    }
}
