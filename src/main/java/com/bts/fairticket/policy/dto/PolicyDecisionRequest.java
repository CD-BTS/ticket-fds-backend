// src/main/java/com/bts/fairticket/policy/dto/PolicyDecisionRequest.java
package com.bts.fairticket.policy.dto;

import jakarta.validation.constraints.NotBlank;

public record PolicyDecisionRequest(
    @NotBlank(message = "context는 필수입니다.")
    String context
) {}