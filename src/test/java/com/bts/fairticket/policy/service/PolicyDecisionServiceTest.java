// src/test/java/com/bts/fairticket/policy/service/PolicyDecisionServiceTest.java
package com.bts.fairticket.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.policy.dto.PolicyDecisionRequest;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class PolicyDecisionServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserCreditRepository userCreditRepository;

    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;

    @Mock
    private PolicyDecisionRepository policyDecisionRepository;

    @InjectMocks
    private PolicyDecisionService policyDecisionService;

    @Test
    @DisplayName("유효하지 않은 context 요청 시 INVALID_CONTEXT 예외 발생")
    void decidePolicy_InvalidContext() {
        PolicyDecisionRequest request = new PolicyDecisionRequest("INVALID_ACTION");

        assertThatThrownBy(() -> policyDecisionService.decidePolicy("user1", "service-1", request))
                .isInstanceOf(ApiException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INVALID_CONTEXT);
    }

    @Test
    @DisplayName("AI 분석 이력이 없어도 신용 점수 기반으로 정상 판정 (anomalyScore=null)")
    void decidePolicy_WithoutAiScore_Success() {
        UserAccount user = new UserAccount("user1", "service-1", "ext-1");
        UserCredit userCredit = new UserCredit(user); // 기본 creditScore = 70

        given(userAccountRepository.findById("user1")).willReturn(Optional.of(user));
        given(userCreditRepository.findById("user1")).willReturn(Optional.of(userCredit));
        given(riskAssessmentRepository.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc("user1"))
                .willReturn(Optional.empty());

        given(policyDecisionRepository.save(any(PolicyDecision.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PolicyDecisionRequest request = new PolicyDecisionRequest("QUEUE_JOIN");
        PolicyDecisionResponse response = policyDecisionService.decidePolicy("user1", "service-1", request);

        assertThat(response.action()).isEqualTo("ALLOW");
        assertThat(response.anomalyScore()).isNull();
    }
}