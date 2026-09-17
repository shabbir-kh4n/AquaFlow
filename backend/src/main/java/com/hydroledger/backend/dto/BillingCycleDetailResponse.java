package com.hydroledger.backend.dto;

import java.util.List;

public record BillingCycleDetailResponse(
        BillingCycleResponse cycle,
        List<InvoiceResponse> invoices
) {
}
