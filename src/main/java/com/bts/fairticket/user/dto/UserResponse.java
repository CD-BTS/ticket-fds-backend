package com.bts.fairticket.user.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.user.entity.UserAccount;

public record UserResponse(
		@JsonProperty("user_id") String userId,
		@JsonProperty("service_id") String serviceId,
		@JsonProperty("external_user_id") String externalUserId,
		@JsonProperty("account_status") String accountStatus,
		@JsonProperty("created_at") LocalDateTime createdAt,
		@JsonProperty("credit_score") Integer creditScore,
		String tier) {

	public static UserResponse from(UserAccount user, UserCredit credit) {
		return new UserResponse(user.getUserId(), user.getServiceId(), user.getExternalUserId(),
				user.getAccountStatus(), user.getCreatedAt(), credit.getCreditScore(), credit.getTier());
	}
}
