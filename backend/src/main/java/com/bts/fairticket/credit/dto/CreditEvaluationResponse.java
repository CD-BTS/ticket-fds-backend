package com.bts.fairticket.credit.dto;

import com.bts.fairticket.credit.service.CreditEvaluationService.EvaluationSummary;

public record CreditEvaluationResponse(
        String userId,
        int oldScore,
        int newScore,
        int changeAmount,
        String grade,
        double baseCredit,
        double dynamicPenalty,
        String penaltyPolicy
) {
    public static CreditEvaluationResponse from(EvaluationSummary summary) {
        return new CreditEvaluationResponse(
                summary.userId(),
                summary.oldScore(),
                summary.newScore(),
                summary.changeAmount(),
                summary.grade(),
                summary.baseCredit(),
                summary.dynamicPenalty(),
                summary.penaltyPolicy()
        );
    }
}