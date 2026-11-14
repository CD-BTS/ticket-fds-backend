package com.bts.fairticket.admin.dto;

import java.time.LocalDateTime;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.risk.entity.RiskAssessment;

public record AdminUserResponse(String userId, String serviceId, String externalUserId,
        String accountStatus, LocalDateTime createdAt, Integer creditScore, String tier,
        Double anomalyScore, LocalDateTime lastAssessedAt) {
    public static AdminUserResponse from(UserResponse user, RiskAssessment assessment) {
        return new AdminUserResponse(user.userId(), user.serviceId(), user.externalUserId(),
                user.accountStatus(), user.createdAt(), user.creditScore(), user.tier(),
                assessment == null ? null : assessment.getAnomalyScore(),
                assessment == null ? null : assessment.getAssessedAt());
    }
}
