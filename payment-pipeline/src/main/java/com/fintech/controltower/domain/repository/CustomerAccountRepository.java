
package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.CustomerAccount;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerAccountRepository
        extends JpaRepository<CustomerAccount, Long> {

    List<CustomerAccount> findByCustomer_IdOrderByCreatedAtDesc(
            Long customerId
    );

    Optional<CustomerAccount> findByIdAndCustomer_Id(
            Long accountId,
            Long customerId
    );

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM CustomerAccount account
            WHERE account.id = :accountId
              AND account.customer.id = :customerId
            """)
    Optional<CustomerAccount> findByIdAndCustomerIdForUpdate(
            @Param("accountId") Long accountId,
            @Param("customerId") Long customerId
    );
}
