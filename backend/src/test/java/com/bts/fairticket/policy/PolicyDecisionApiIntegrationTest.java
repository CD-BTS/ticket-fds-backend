package com.bts.fairticket.policy;
import com.bts.fairticket.common.auth.ApiKeyFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@Tag("mysql")
@SpringBootTest
@Transactional
class PolicyDecisionApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @Autowired
    private PolicyDecisionRepository policyDecisionRepository;

    private static final String SERVICE_ID = "service-1";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("[201] 올바른 serviceId와 context로 요청 시 정책 결정 성공 및 영속화")
    void decidePolicy_Success() throws Exception {
        // Given: 유저 생성 및 Credit 영속화
        UserAccount user = new UserAccount("policy-user-1", SERVICE_ID, "ext-user-1");
        UserAccount savedUser = userAccountRepository.saveAndFlush(user);
        userCreditRepository.saveAndFlush(new UserCredit(savedUser));

        // 최신 AI 위험도 평가 적재 (0.80 -> DELAY 예상)
        RiskAssessment risk = new RiskAssessment(savedUser, 0.80, "HIGH_RISK");
        riskAssessmentRepository.saveAndFlush(risk);

        String jsonBody = "{\"context\":\"QUEUE_JOIN\"}";

        // When & Then
        mockMvc.perform(post("/api/v1/users/{userId}/policy-decisions", savedUser.getUserId())
                    .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, SERVICE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(savedUser.getUserId()))
                .andExpect(jsonPath("$.data.action").value("DELAY"))
                .andExpect(jsonPath("$.data.anomalyScore").value(0.80));
    }

    @Test
    @DisplayName("[403] 타 서비스의 유저에 대해 정책 결정을 요청하면 FORBIDDEN 발생")
    void decidePolicy_Forbidden_DifferentService() throws Exception {
        // Given: 'other-service' 소속 유저
        UserAccount user = new UserAccount("other-user-1", "other-service", "ext-2");
        UserAccount savedUser = userAccountRepository.saveAndFlush(user);
        userCreditRepository.saveAndFlush(new UserCredit(savedUser));

        String jsonBody = "{\"context\":\"BOOKING_ATTEMPT\"}";

        // When & Then: 인증된 서비스는 'service-1'
        mockMvc.perform(post("/api/v1/users/{userId}/policy-decisions", savedUser.getUserId())
                        .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, SERVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("[400] 허용되지 않은 context 입력 시 INVALID_CONTEXT 예외 반환")
    void decidePolicy_InvalidContext() throws Exception {
        UserAccount user = new UserAccount("policy-user-2", SERVICE_ID, "ext-3");
        UserAccount savedUser = userAccountRepository.saveAndFlush(user);
        userCreditRepository.saveAndFlush(new UserCredit(savedUser));

        String jsonBody = "{\"context\":\"INVALID_ACTION_NAME\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/policy-decisions", savedUser.getUserId())
                        .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, SERVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTEXT"));
    }
}