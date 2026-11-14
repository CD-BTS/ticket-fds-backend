package com.bts.fairticket.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UserRegistrationRequest(
		@JsonProperty("serviceId") @NotBlank @Size(max = 50) String serviceId,
		@JsonProperty("externalUserId") @NotBlank @Size(max = 100) String externalUserId,
		@Pattern(regexp = "ACTIVE|SUSPENDED") String accountStatus) {

	public UserRegistrationRequest {
		accountStatus = accountStatus == null ? "ACTIVE" : accountStatus;
	}

	public UserRegistrationRequest(String serviceId, String externalUserId) {
		this(serviceId, externalUserId, "ACTIVE");
	}
}
