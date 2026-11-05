// src/main/java/com/bts/fairticket/policy/dto/PolicyDecisionResponse.java
package com.bts.fairticket.policy.dto;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.bts.fairticket.policy.entity.PolicyDecision;

public record PolicyDecisionResponse(
    Long decisionId,
    String userId,
    String context,
    Integer creditScore,
    Double anomalyScore,
    String action,
    String reason,
    String decidedAt
) {
    public static PolicyDecisionResponse from(PolicyDecision decision) {
        String formattedDecidedAt = null;
        if (decision.getDecidedAt() != null) {
            formattedDecidedAt = decision.getDecidedAt()
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        return new PolicyDecisionResponse(
            decision.getDecisionId(),
            decision.getUser().getUserId(),
            decision.getContext(),
            decision.getCreditScoreAtDecision(),
            decision.getAnomalyScoreAtDecision(),
            decision.getAction(),
            decision.getReason(),
            formattedDecidedAt
        );
    }
}