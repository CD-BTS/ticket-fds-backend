package com.bts.fairticket.credit.dto;

public record CreditEvaluationRequest(
        String triggerReason,
        Integer recentIpAttemptCount,
        Double intervalVariance
) {
    public CreditEvaluationContext toContext() {
        return new CreditEvaluationContext(
                triggerReason != null ? triggerReason : "MANUAL_OR_EVENT_EVALUATION",
                recentIpAttemptCount != null ? recentIpAttemptCount : 0,
                intervalVariance
        );
    }
}