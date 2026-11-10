
package com.fintech.controltower.api.dto;

import java.time.LocalDateTime;

public record IsoMessageResponse(
        Long id,
        String uetr,
        String messageType,
        String schemaVersion,
        String validationStatus,
        LocalDateTime createdAt,
        String messageContent
) {
}
