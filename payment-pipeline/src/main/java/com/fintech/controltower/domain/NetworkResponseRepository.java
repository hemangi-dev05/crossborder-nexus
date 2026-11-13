
package com.fintech.controltower.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NetworkResponseRepository
        extends JpaRepository<NetworkResponse, Long> {

    Optional<NetworkResponse> findByNetworkSubmission_Id(Long submissionId);
}
