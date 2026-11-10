
package com.fintech.controltower.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IsoMessageRepository extends JpaRepository<IsoMessage, Long> {

    // Retrieve all ISO messages associated with a payment.
    List<IsoMessage> findByTransactionLedger_UetrOrderByCreatedAtAsc(
            String uetr
    );
}

