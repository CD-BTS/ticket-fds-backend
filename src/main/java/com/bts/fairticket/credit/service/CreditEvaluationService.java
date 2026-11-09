package com.bts.fairticket.credit.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.dto.CreditEvaluationContext;
import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCard;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.policy.CreditCalculator;
import com.bts.fairticket.credit.policy.CreditCalculator.CalculationInput;
import com.bts.fairticket.credit.policy.CreditCalculator.CalculationResult;
import com.bts.fairticket.credit.policy.CreditPolicyConstants;
import com.bts.fairticket.credit.repository.BookingHistoryRepository;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCardRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditEvaluationService {

    private final UserAccountRepository userAccountRepository;
    private final UserCreditRepository userCreditRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final UserCardRepository userCardRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final CreditCalculator creditCalculator;

    @Transactional
    public EvaluationSummary evaluateAndApplyCredit(String userId, CreditEvaluationContext context) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));

        UserCredit userCredit = userCreditRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.CREDIT_NOT_FOUND));

        int oldScore = userCredit.getCreditScore();

        // 1. 관람 이력 통계 집계 (A, N, C_auto)
        var bookingStats = bookingHistoryRepository.getBookingStatsByUserId(userId);
        long completedCount = (bookingStats != null && bookingStats.getCompletedCount() != null) 
                ? bookingStats.getCompletedCount() : 0L;
        long noShowCount = (bookingStats != null && bookingStats.getNoShowCount() != null) 
                ? bookingStats.getNoShowCount() : 0L;
        long autoCancelledCount = (bookingStats != null && bookingStats.getAutoCancelledCount() != null) 
                ? bookingStats.getAutoCancelledCount() : 0L;

        // 2. 최신 AI 이상도 조회
        Optional<RiskAssessment> latestRisk =
                riskAssessmentRepository.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(userId);
        Double anomalyScore = latestRisk.map(RiskAssessment::getAnomalyScore).orElse(null);

        // 3. 룰 기반 감점 계산 (카드 공유, IP 집중도, 요청 분산)
        double rulePenalty = calculateRulePenalty(userId, context);

        // 4. 수식 계산 컴포넌트 호출
        CalculationInput input = new CalculationInput(
                completedCount,
                noShowCount,
                autoCancelledCount,
                anomalyScore,
                rulePenalty
        );
        CalculationResult result = creditCalculator.calculate(input);

        int newScore = result.finalCreditScore();
        int changeAmount = newScore - oldScore;

        // 5. UserCredit 갱신
        userCredit.updateCreditScore(newScore, result.grade().name());
        userCreditRepository.save(userCredit);

        // 6. CreditHistory 이력 적재
        String reason = (context != null && context.triggerReason() != null) 
                ? context.triggerReason() 
                : "Credit 정기/동적 재산출";

        CreditHistory history = new CreditHistory(
                user,
                oldScore,  // scoreBefore
                newScore,  // scoreAfter
                reason
        );
        creditHistoryRepository.save(history);

        log.info("User {} credit evaluated: {} -> {} (change: {}), grade: {}", 
                userId, oldScore, newScore, changeAmount, result.grade());

        return new EvaluationSummary(
                userId,
                oldScore,
                newScore,
                changeAmount,
                result.grade().name(),
                result.baseCredit(),
                result.dynamicPenalty(),
                result.penaltyPolicy()
        );
    }

    private double calculateRulePenalty(String userId, CreditEvaluationContext context) {
        double penalty = 0.0;

        // ① 카드 공유 감점 (사용자가 등록한 카드 중 최대 공유 계정 수 기준)
        List<UserCard> cards = userCardRepository.findByUser_UserId(userId);
        long maxSharedAccounts = 0;
        for (UserCard card : cards) {
            long count = userCardRepository.countDistinctUserByCardHash(card.getCardHash());
            if (count > maxSharedAccounts) {
                maxSharedAccounts = count;
            }
        }

        if (maxSharedAccounts >= 4) {
            penalty += CreditPolicyConstants.PENALTY_CARD_SHARING_HIGH; // -15
        } else if (maxSharedAccounts >= 2) {
            penalty += CreditPolicyConstants.PENALTY_CARD_SHARING_MID;  // -5
        }

        // ② IP 집중도 감점 (최근 10분간 5회 이상 시도)
        if (context != null && context.recentIpAttemptCount() != null && context.recentIpAttemptCount() >= 5) {
            penalty += CreditPolicyConstants.PENALTY_IP_BURST; // -5
        }

        // ③ 요청 간격 분산 감점 (1.0초^2 미만)
        if (context != null && context.intervalVariance() != null && context.intervalVariance() < 1.0) {
            penalty += CreditPolicyConstants.PENALTY_LOW_INTERVAL_VARIANCE; // -5
        }

        return Math.min(CreditPolicyConstants.MAX_RULE_PENALTY, penalty);
    }

    public record EvaluationSummary(
            String userId,
            int oldScore,
            int newScore,
            int changeAmount,
            String grade,
            double baseCredit,
            double dynamicPenalty,
            String penaltyPolicy
    ) {}
}