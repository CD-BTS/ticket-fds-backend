// src/main/java/com/bts/fairticket/policy/controller/PolicyDecisionController.java
package com.bts.fairticket.policy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.policy.dto.PolicyDecisionRequest;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;
import com.bts.fairticket.policy.service.PolicyDecisionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/{userId}/policy-decisions")
@RequiredArgsConstructor
public class PolicyDecisionController {

    private final PolicyDecisionService policyDecisionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyDecisionResponse>> createPolicyDecision(
            @PathVariable String userId,
            @RequestAttribute(value = ApiKeyFilter.SERVICE_ATTRIBUTE, required = false) String authenticatedService,
            @Valid @RequestBody PolicyDecisionRequest request) {

        PolicyDecisionResponse response = policyDecisionService.decidePolicy(userId, authenticatedService, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ResponseCode.CREATED, response, "대응 정책이 결정되었습니다."));
    }
}