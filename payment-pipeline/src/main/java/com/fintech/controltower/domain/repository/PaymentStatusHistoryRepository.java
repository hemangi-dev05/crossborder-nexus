
package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.PaymentStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentStatusHistoryRepository
        extends JpaRepository<PaymentStatusHistory, Long> {

    List<PaymentStatusHistory> findByUetrOrderByChangedAtAsc(String uetr);
}
