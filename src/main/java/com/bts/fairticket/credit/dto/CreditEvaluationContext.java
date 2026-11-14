package com.bts.fairticket.credit.dto;

public record CreditEvaluationContext(
        String triggerReason,          // 점수 갱신 트리거 사유 (예: "정기 평가", "예매 완료 후 갱신" 등)
        Integer recentIpAttemptCount,  // 동일 IP 최근 10분간 예매 시도 횟수 (5회 이상 시 감점)
        Double intervalVariance        // 요청 간격 분산 (1.0 미만 시 감점)
) {
    public static CreditEvaluationContext defaultContext() {
        return new CreditEvaluationContext("SYSTEM_EVALUATION", 0, null);
    }
}