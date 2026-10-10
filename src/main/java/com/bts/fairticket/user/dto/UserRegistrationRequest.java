package com.bts.fairticket.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
		@JsonProperty("service_id") @NotBlank @Size(max = 50) String serviceId,
		@JsonProperty("external_user_id") @NotBlank @Size(max = 100) String externalUserId) {
}
