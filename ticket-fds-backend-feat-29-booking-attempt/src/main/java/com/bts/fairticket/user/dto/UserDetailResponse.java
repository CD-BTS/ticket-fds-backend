package com.bts.fairticket.user.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.risk.entity.RiskAssessment;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserDetailResponse(
		String userId, String serviceId, String externalUserId, String accountStatus,
		LocalDateTime createdAt, Integer creditScore, String tier,
		Double anomalyScore, LocalDateTime lastAssessedAt, String latestAction) {

	public static UserDetailResponse from(UserResponse user, RiskAssessment assessment, PolicyDecision policy) {
		return new UserDetailResponse(user.userId(), user.serviceId(), user.externalUserId(), user.accountStatus(),
				user.createdAt(), user.creditScore(), user.tier(),
				assessment == null ? null : assessment.getAnomalyScore(),
				assessment == null ? null : assessment.getAssessedAt(),
				policy == null ? null : policy.getAction());
	}
}
