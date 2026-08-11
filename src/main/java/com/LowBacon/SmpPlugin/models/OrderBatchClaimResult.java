package com.bx.finnishSmp.models;

public record OrderBatchClaimResult(
        int itemClaims,
        int refundClaims,
        int failedClaims,
        int itemAmount,
        double refundAmount
) {
}
