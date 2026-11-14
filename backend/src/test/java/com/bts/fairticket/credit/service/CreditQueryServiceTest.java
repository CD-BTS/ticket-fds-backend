package com.bts.fairticket.credit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class CreditQueryServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserCreditRepository userCreditRepository;

    @Mock
    private CreditHistoryRepository creditHistoryRepository;

    @InjectMocks
    private CreditQueryService creditQueryService;

    @Test
    @DisplayName("사용자는 존재하지만 Credit 정보가 누락된 경우 CREDIT_NOT_FOUND 예외 발생")
    void getCredit_CreditNotFound() {
        given(userAccountRepository.existsById("bts_user_001")).willReturn(true);
        given(userCreditRepository.findById("bts_user_001")).willReturn(Optional.empty());

        assertThatThrownBy(() -> creditQueryService.getCredit("bts_user_001"))
                .isInstanceOf(ApiException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.CREDIT_NOT_FOUND);
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦은 경우 INVALID_QUERY_PARAMETER 예외 발생")
    void getCreditHistory_InvalidDateRange() {
        given(userAccountRepository.existsById("bts_user_001")).willReturn(true);

        assertThatThrownBy(() -> creditQueryService.getCreditHistory(
                "bts_user_001", 0, 20, "2026-10-15", "2026-10-10"))
                .isInstanceOf(ApiException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID_QUERY_PARAMETER);
    }

    @Test
    @DisplayName("size가 100을 초과하면 INVALID_QUERY_PARAMETER 예외 발생")
    void getCreditHistory_SizeExceeded() {
        given(userAccountRepository.existsById("bts_user_001")).willReturn(true);

        assertThatThrownBy(() -> creditQueryService.getCreditHistory(
                "bts_user_001", 0, 101, null, null))
                .isInstanceOf(ApiException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID_QUERY_PARAMETER);
    }
}
