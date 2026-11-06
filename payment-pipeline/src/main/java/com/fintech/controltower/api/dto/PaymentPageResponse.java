package com.fintech.controltower.api.dto;

import java.util.List;

public record PaymentPageResponse(
        List<PaymentDetailsResponse> payments,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last
) {}