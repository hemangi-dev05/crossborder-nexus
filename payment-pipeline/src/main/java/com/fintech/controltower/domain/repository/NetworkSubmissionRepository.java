package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.NetworkSubmission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NetworkSubmissionRepository
        extends JpaRepository<NetworkSubmission, Long> {

    List<NetworkSubmission> findByTransactionLedger_UetrOrderBySubmittedAtAsc(
            String uetr
    );
}