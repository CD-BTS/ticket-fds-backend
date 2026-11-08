package com.bts.fairticket.user.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.user.entity.UserAccount;

public record UserResponse(
		@JsonProperty("userId") String userId,
		@JsonProperty("serviceId") String serviceId,
		@JsonProperty("externalUserId") String externalUserId,
		@JsonProperty("accountStatus") String accountStatus,
		@JsonProperty("createdAt") LocalDateTime createdAt,
		@JsonProperty("creditScore") Integer creditScore,
		String tier) {

	public static UserResponse from(UserAccount user, UserCredit credit) {
		return new UserResponse(user.getUserId(), user.getServiceId(), user.getExternalUserId(),
				user.getAccountStatus(), user.getCreatedAt(), credit.getCreditScore(), credit.getTier());
	}
}
