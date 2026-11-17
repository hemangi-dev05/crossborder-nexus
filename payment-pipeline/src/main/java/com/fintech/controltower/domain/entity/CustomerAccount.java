
package com.fintech.controltower.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "customer_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_account_number",
                        columnNames = "account_number"
                )
        },
        indexes = {
                @Index(
                        name = "idx_customer_account_customer",
                        columnList = "customer_id"
                )
        }
)
public class CustomerAccount {

    public enum AccountType {
        SAVINGS
    }

    public enum AccountStatus {
        ACTIVE,
        FROZEN,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "account_number",
            nullable = false,
            unique = true,
            length = 20
    )
    private String accountNumber;

    @Column(
            name = "account_holder_name",
            nullable = false,
            length = 150
    )
    private String accountHolderName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(
            name = "current_balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(
            name = "reserved_balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CustomerAccount() {
    }

    public CustomerAccount(
            String accountNumber,
            Customer customer,
            String accountHolderName,
            AccountType accountType,
            String currency
    ) {
        this.accountNumber = Objects.requireNonNull(accountNumber);
        this.customer = Objects.requireNonNull(customer);
        this.accountHolderName =
                Objects.requireNonNull(accountHolderName);
        this.accountType = Objects.requireNonNull(accountType);
        this.currency = Objects.requireNonNull(currency);

        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a three-letter uppercase code."
            );
        }

        this.currentBalance = BigDecimal.ZERO;
        this.reservedBalance = BigDecimal.ZERO;
        this.status = AccountStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public BigDecimal getAvailableBalance() {
        return currentBalance.subtract(reservedBalance);
    }

    public void reserve(BigDecimal amount) {
        validatePositiveAmount(amount);
        ensureActive();

        if (getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient available balance."
            );
        }

        reservedBalance = reservedBalance.add(amount);
    }

    public void release(BigDecimal amount) {
        validatePositiveAmount(amount);

        if (reservedBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Cannot release more than the reserved balance."
            );
        }

        reservedBalance = reservedBalance.subtract(amount);
    }

    public void debitReserved(BigDecimal amount) {
        validatePositiveAmount(amount);

        if (reservedBalance.compareTo(amount) < 0
                || currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient reserved or current balance."
            );
        }

        reservedBalance = reservedBalance.subtract(amount);
        currentBalance = currentBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        validatePositiveAmount(amount);
        ensureActive();

        currentBalance = currentBalance.add(amount);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero."
            );
        }

        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Account operations support a maximum of two decimal places."
            );
        }
    }

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "This account is not active."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setStatus(AccountStatus status) {
        this.status = Objects.requireNonNull(status);
    }
}
