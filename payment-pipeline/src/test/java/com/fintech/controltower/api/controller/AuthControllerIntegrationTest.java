
package com.fintech.controltower.api.controller;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.domain.entity.CustomerAccount;
import com.fintech.controltower.domain.entity.CustomerRole;
import com.fintech.controltower.domain.repository.CustomerAccountRepository;
import com.fintech.controltower.domain.repository.CustomerRepository;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        customerRepository.deleteAll();
    }

    // --------------------------------------------------
    // TEST 1: Customer registration and initial account
    // --------------------------------------------------

    @Test
    void shouldRegisterCustomerAndStoreEncryptedPassword()
            throws Exception {

        String password = "SecurePass123";

        String request = """
                {
                    "fullName": "Test Customer",
                    "email": "customer@test.com",
                    "password": "SecurePass123",
                    "countryCode": "IN"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName")
                        .value("Test Customer"))
                .andExpect(jsonPath("$.email")
                        .value("customer@test.com"))
                .andExpect(jsonPath("$.role")
                        .value("CUSTOMER"))
                .andExpect(jsonPath("$.countryCode")
                        .value("IN"));

        Customer customer = customerRepository
                .findByEmail("customer@test.com")
                .orElseThrow();

        assertThat(customer.getPasswordHash())
                .isNotEqualTo(password);

        assertThat(passwordEncoder.matches(
                password,
                customer.getPasswordHash()
        )).isTrue();

        // Verify automatic INR Savings account creation
        var accounts = accountRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId());

        assertThat(accounts).hasSize(1);

        CustomerAccount account = accounts.get(0);

        assertThat(account.getAccountHolderName())
                .isEqualTo("Test Customer");

        assertThat(account.getAccountType())
                .isEqualTo(CustomerAccount.AccountType.SAVINGS);

        assertThat(account.getCurrency())
                .isEqualTo("INR");

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("0.00");

        assertThat(account.getReservedBalance())
                .isEqualByComparingTo("0.00");

        assertThat(account.getStatus())
                .isEqualTo(CustomerAccount.AccountStatus.ACTIVE);

        assertThat(account.getAccountNumber())
                .hasSize(12);
    }

    // --------------------------------------------------
    // TEST 2: Duplicate email registration
    // --------------------------------------------------

    @Test
    void shouldRejectDuplicateEmailRegistration()
            throws Exception {

        String request = """
                {
                    "fullName": "Test Customer",
                    "email": "duplicate@test.com",
                    "password": "SecurePass123",
                    "countryCode": "IN"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------
    // TEST 3: Invalid password
    // --------------------------------------------------

    @Test
    void shouldRejectPasswordShorterThanEightCharacters()
            throws Exception {

        String request = """
                {
                    "fullName": "Test Customer",
                    "email": "short@test.com",
                    "password": "123",
                    "countryCode": "IN"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());

        assertThat(customerRepository.existsByEmail("short@test.com"))
                .isFalse();
    }

    // --------------------------------------------------
    // TEST 4: Successful login
    // --------------------------------------------------

    @Test
    void shouldAuthenticateCustomerAndCreateSession()
            throws Exception {

        registerTestCustomer();

        String request = """
                {
                    "email": "customer@test.com",
                    "password": "SecurePass123"
                }
                """;

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Login successful"))
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        Object securityContext = session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        );

        assertThat(securityContext)
                .isInstanceOf(SecurityContext.class);

        SecurityContext context = (SecurityContext) securityContext;

        assertThat(context.getAuthentication().isAuthenticated())
                .isTrue();

        assertThat(context.getAuthentication().getName())
                .isEqualTo("customer@test.com");
    }

    // --------------------------------------------------
    // TEST 5: Invalid login credentials
    // --------------------------------------------------

    @Test
    void shouldRejectInvalidLoginCredentials()
            throws Exception {

        registerTestCustomer();

        String request = """
                {
                    "email": "customer@test.com",
                    "password": "WrongPassword123"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    // --------------------------------------------------
    // TEST 6: Logout
    // --------------------------------------------------

    @Test
    void shouldInvalidateSessionOnLogout()
            throws Exception {

        registerTestCustomer();

        String loginRequest = """
                {
                    "email": "customer@test.com",
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
                (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isFalse();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .session(session)
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    // --------------------------------------------------
    // HELPER: Register a test customer
    // --------------------------------------------------

    private void registerTestCustomer() {

        Customer customer = new Customer(
                "Test Customer",
                "customer@test.com",
                passwordEncoder.encode("SecurePass123"),
                null,
                "IN",
                null,
                CustomerRole.CUSTOMER
        );

        customerRepository.saveAndFlush(customer);
    }
}
