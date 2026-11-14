package com.bts.fairticket.credit.controller;

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
import com.bts.fairticket.credit.dto.CreditEvaluationRequest;
import com.bts.fairticket.credit.dto.CreditEvaluationResponse;
import com.bts.fairticket.credit.service.CreditEvaluationService;
import com.bts.fairticket.credit.service.CreditEvaluationService.EvaluationSummary;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/{userId}/credit")
@RequiredArgsConstructor
public class CreditEvaluationController {

    private final CreditEvaluationService creditEvaluationService;

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<CreditEvaluationResponse>> evaluateCredit(
            @PathVariable String userId,
            @RequestAttribute(value = ApiKeyFilter.SERVICE_ATTRIBUTE, required = false) String authenticatedService,
            @RequestBody(required = false) CreditEvaluationRequest request) {

        CreditEvaluationRequest safeRequest = (request != null) 
                ? request 
                : new CreditEvaluationRequest(null, null, null);

        EvaluationSummary summary = creditEvaluationService.evaluateAndApplyCredit(
                userId, 
                safeRequest.toContext()
        );

        CreditEvaluationResponse response = CreditEvaluationResponse.from(summary);

        return ResponseEntity.ok(ApiResponse.success(ResponseCode.SUCCESS, response, "신용 점수가 성공적으로 재산출되었습니다."));
    }
}