package com.bts.fairticket.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bts.fairticket.credit.dto.CreditEvaluationContext;
import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCard;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.policy.CreditCalculator;
import com.bts.fairticket.credit.repository.BookingHistoryRepository;
import com.bts.fairticket.credit.repository.BookingHistoryRepository.BookingStats;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCardRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class CreditEvaluationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private UserCreditRepository userCreditRepository;
    @Mock
    private BookingHistoryRepository bookingHistoryRepository;
    @Mock
    private UserCardRepository userCardRepository;
    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;
    @Mock
    private CreditHistoryRepository creditHistoryRepository;

    @Spy
    private CreditCalculator creditCalculator = new CreditCalculator();

    @InjectMocks
    private CreditEvaluationService creditEvaluationService;

    @Test
    @DisplayName("관람 완료 10회 우수 유저: 점수 50점에서 75점으로 갱신되고 이력이 적재된다")
    void evaluateAndApplyCredit_GoodUser() {
        // Given
        String userId = "good-user-1";
        UserAccount user = new UserAccount(userId, "service-1", "ext-good");
        UserCredit userCredit = new UserCredit(user); // 초기 50점 또는 100점

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(userCreditRepository.findById(userId)).willReturn(Optional.of(userCredit));

        // 관람 이력: COMPLETED 10건, NO_SHOW 0건, AUTO_CANCELLED 0건
        BookingStats stats = new BookingStats() {
            public Long getCompletedCount() { return 10L; }
            public Long getNoShowCount() { return 0L; }
            public Long getAutoCancelledCount() { return 0L; }
        };
        given(bookingHistoryRepository.getBookingStatsByUserId(userId)).willReturn(stats);
        given(riskAssessmentRepository.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(userId))
                .willReturn(Optional.empty());
        given(userCardRepository.findByUser_UserId(userId)).willReturn(List.of());

        CreditEvaluationContext context = new CreditEvaluationContext("정기 평가", 0, 2.5);

        // When
        var summary = creditEvaluationService.evaluateAndApplyCredit(userId, context);

        // Then
        // BaseCredit = 100 * (10+5)/(10+10) = 75점, 감점 0 -> 최종 75점 (NORMAL)
        assertThat(summary.newScore()).isEqualTo(75);
        assertThat(summary.grade()).isEqualTo("NORMAL");
        assertThat(userCredit.getCreditScore()).isEqualTo(75);

        verify(creditHistoryRepository).save(any(CreditHistory.class));
        verify(userCreditRepository).save(userCredit);
    }

    @Test
    @DisplayName("카드 4계정 공유 및 AI 이상치 탐지 시 고위험군(HIGH_RISK)으로 감점 갱신")
    void evaluateAndApplyCredit_HighRiskUser() {
        // Given
        String userId = "risky-user-1";
        UserAccount user = new UserAccount(userId, "service-1", "ext-risky");
        UserCredit userCredit = new UserCredit(user);

        given(userAccountRepository.findById(userId)).willReturn(Optional.of(user));
        given(userCreditRepository.findById(userId)).willReturn(Optional.of(userCredit));
        given(bookingHistoryRepository.getBookingStatsByUserId(userId)).willReturn(null);

        // AI 이상도 0.75 (30점 감점)
        RiskAssessment risk = new RiskAssessment(user, 0.75, "HIGH_RISK");
        given(riskAssessmentRepository.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(userId))
                .willReturn(Optional.of(risk));

        // 카드 4계정 공유 (-15점 감점)
        UserCard card = new UserCard(user, "hash-1234", "현대", "1234-****-****-1234");
        given(userCardRepository.findByUser_UserId(userId)).willReturn(List.of(card));
        given(userCardRepository.countDistinctUserByCardHash("hash-1234")).willReturn(4L);

        CreditEvaluationContext context = CreditEvaluationContext.defaultContext();

        // When
        var summary = creditEvaluationService.evaluateAndApplyCredit(userId, context);

        // Then
        // BaseCredit 50 - DynamicPenalty 45 (AI 30 + Card 15) = 5점 (HIGH_RISK)
        assertThat(summary.newScore()).isEqualTo(5);
        assertThat(summary.grade()).isEqualTo("HIGH_RISK");
        assertThat(summary.penaltyPolicy()).contains("후순위");

        verify(creditHistoryRepository).save(any(CreditHistory.class));
    }
}