package com.bts.fairticket.credit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.dto.CreditHistoryResponse;
import com.bts.fairticket.credit.dto.CreditResponse;
import com.bts.fairticket.credit.service.CreditQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class CreditController {

    private final CreditQueryService creditQueryService;

    @GetMapping("/credit")
    public ResponseEntity<ApiResponse<CreditResponse>> getCredit(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String userId) {

        validateAdminAuth(authHeader);
        CreditResponse response = creditQueryService.getCredit(userId);
        return ResponseEntity.ok(ApiResponse.success(ResponseCode.SUCCESS, response, "관람 신용 정보를 조회했습니다."));
    }

    @GetMapping("/credit-history")
    public ResponseEntity<ApiResponse<CreditHistoryResponse>> getCreditHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        validateAdminAuth(authHeader);
        CreditHistoryResponse response = creditQueryService.getCreditHistory(userId, page, size, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(ResponseCode.SUCCESS, response, "신용 점수 변동 이력을 조회했습니다."));
    }

    private void validateAdminAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.substring(7).isBlank()) {
            throw new ApiException(ResponseCode.UNAUTHORIZED);
        }
        // TODO: 관리자 JWT 토큰 또는 서명 검증 로직 추가 (MVP 단계에서는 Bearer 형식 유효성 우선 적용)
    }
}