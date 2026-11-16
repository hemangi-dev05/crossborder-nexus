
package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.NetworkResponse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkResponseRepository
        extends JpaRepository<NetworkResponse, Long> {

    Optional<NetworkResponse> findByNetworkSubmission_Id(Long submissionId);

    List<NetworkResponse> findByNetworkSubmission_IdIn(
            Collection<Long> submissionIds
    );
}
