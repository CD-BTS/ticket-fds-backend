// src/main/java/com/bts/fairticket/policy/service/PolicyDecisionService.java
package com.bts.fairticket.policy.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.policy.dto.PolicyDecisionRequest;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PolicyDecisionService {

    private static final Set<String> ALLOWED_CONTEXTS = Set.of("QUEUE_JOIN", "BOOKING_ATTEMPT");

    private final UserAccountRepository userAccountRepository;
    private final UserCreditRepository userCreditRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final PolicyDecisionRepository policyDecisionRepository;

    @Transactional
    public PolicyDecisionResponse decidePolicy(String userId, String authenticatedService, PolicyDecisionRequest request) {
        // 1. context 파라미터 유효성 검증
        if (request.context() == null || !ALLOWED_CONTEXTS.contains(request.context().trim())) {
            throw new ApiException(ResponseCode.INVALID_CONTEXT);
        }

        // 2. 사용자 조회 및 소속 서비스 검증
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));

        if (authenticatedService != null && !authenticatedService.equals(user.getServiceId())) {
            throw new ApiException(ResponseCode.FORBIDDEN);
        }

        // 3. UserCredit 조회
        UserCredit userCredit = userCreditRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.CREDIT_NOT_FOUND));
        int creditScore = userCredit.getCreditScore();

        // 4. 최신 RiskAssessment 조회
        Optional<RiskAssessment> latestAssessmentOpt =
                riskAssessmentRepository.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(userId);

        Double anomalyScore = latestAssessmentOpt.map(RiskAssessment::getAnomalyScore).orElse(null);
        RiskAssessment assessment = latestAssessmentOpt.orElse(null);

        // 5. 대응 정책(Action) 및 사유(Reason) 종합 판단
        DecisionResult decisionResult = evaluatePolicy(creditScore, anomalyScore);

        // 6. DB 저장 (policy_decision 테이블)
        PolicyDecision policyDecision = new PolicyDecision(
                user,
                assessment,
                request.context().trim(),
                creditScore,
                anomalyScore,
                decisionResult.action(),
                decisionResult.reason()
        );
        policyDecision = policyDecisionRepository.save(policyDecision);

        return PolicyDecisionResponse.from(policyDecision);
    }

    private DecisionResult evaluatePolicy(int creditScore, Double anomalyScore) {
        // AI 분석 이력이 없는 경우: Credit 점수 기반 기본 판정
        if (anomalyScore == null) {
            if (creditScore >= 40) {
                return new DecisionResult("ALLOW", "신용 점수 정상 (AI 분석 이력 없음)");
            } else if (creditScore >= 20) {
                return new DecisionResult("REQUIRE_CAPTCHA", "신용 점수 의심 구간 (CAPTCHA 추가 인증 필요)");
            } else {
                return new DecisionResult("DELAY", "신용 점수 고위험 구간 (대기열 후순위 및 CAPTCHA 필요)");
            }
        }

        // AI 이상도 점수가 있는 경우: Credit 점수와 AnomalyScore 종합 판정
        if (creditScore < 20 && anomalyScore >= 0.85) {
            return new DecisionResult("RESTRICT", "극단적 이상 행동 및 최하위 신용 점수 감지 (예매 제한)");
        } else if (creditScore < 20 || anomalyScore >= 0.70) {
            return new DecisionResult("DELAY", "위험 사용자 판정: 이상 행동 또는 누적 신용 점수 미달 (대기열 후순위)");
        } else if (creditScore < 40 || anomalyScore >= 0.40) {
            return new DecisionResult("REQUIRE_CAPTCHA", "의심 사용자 판정: 추가 인증을 통한 자동화 여부 검증");
        } else {
            return new DecisionResult("ALLOW", "정상 사용자 판정: 일반 대기열 입장 허용");
        }
    }

    private record DecisionResult(String action, String reason) {}
}