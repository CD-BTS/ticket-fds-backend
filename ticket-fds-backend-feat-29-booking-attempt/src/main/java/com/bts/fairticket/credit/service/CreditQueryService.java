package com.bts.fairticket.credit.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.dto.CreditHistoryResponse;
import com.bts.fairticket.credit.dto.CreditResponse;
import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditQueryService {

    private final UserAccountRepository userAccountRepository;
    private final UserCreditRepository userCreditRepository;
    private final CreditHistoryRepository creditHistoryRepository;

    public CreditResponse getCredit(String userId) {
        validateUserExists(userId);

        UserCredit userCredit = userCreditRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.CREDIT_NOT_FOUND));

        return CreditResponse.from(userCredit);
    }

    public CreditHistoryResponse getCreditHistory(String userId, int page, int size, String startDate, String endDate) {
        validateUserExists(userId);

        // 쿼리 파라미터 유효성 검증 (size 최대 100, page >= 0)
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(ResponseCode.INVALID_QUERY_PARAMETER);
        }

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            if (startDate != null && !startDate.isBlank()) {
                startDateTime = LocalDate.parse(startDate).atStartOfDay();
            }
            if (endDate != null && !endDate.isBlank()) {
                endDateTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            }
        } catch (DateTimeParseException e) {
            throw new ApiException(ResponseCode.INVALID_QUERY_PARAMETER);
        }

        // 시작일이 종료일보다 늦은 경우 에러
        if (startDateTime != null && endDateTime != null && startDateTime.isAfter(endDateTime)) {
            throw new ApiException(ResponseCode.INVALID_QUERY_PARAMETER);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<CreditHistory> historyPage = creditHistoryRepository.findByUserIdAndDateRange(
                userId, startDateTime, endDateTime, pageable);

        return CreditHistoryResponse.of(userId, historyPage);
    }

    private void validateUserExists(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }
        if (!userAccountRepository.existsById(userId)) {
            throw new ApiException(ResponseCode.USER_NOT_FOUND);
        }
    }
}