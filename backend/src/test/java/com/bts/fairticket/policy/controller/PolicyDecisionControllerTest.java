package com.bts.fairticket.policy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.policy.dto.PolicyDecisionRequest;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;
import com.bts.fairticket.policy.service.PolicyDecisionService;

@ExtendWith(MockitoExtension.class)
class PolicyDecisionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PolicyDecisionService policyDecisionService;

    @InjectMocks
    private PolicyDecisionController policyDecisionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyDecisionController).build();
    }

    @Test
    @DisplayName("POST /policy-decisions - 201 Created 응답과 결정된 정책을 반환한다")
    void createPolicyDecision_Success() throws Exception {
        PolicyDecisionResponse response = new PolicyDecisionResponse(
                701L, "bts_user_001", "QUEUE_JOIN", 35, 0.82, "DELAY",
                "반복적인 예매 및 취소 패턴 감지", "2026-11-04T14:32:00+09:00");

        given(policyDecisionService.decidePolicy(anyString(), any(), any(PolicyDecisionRequest.class)))
                .willReturn(response);

        String jsonBody = "{\"context\": \"QUEUE_JOIN\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/policy-decisions", "bts_user_001")
                        .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, "service-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_201"))
                .andExpect(jsonPath("$.data.decisionId").value(701))
                .andExpect(jsonPath("$.data.action").value("DELAY"))
                .andExpect(jsonPath("$.data.anomalyScore").value(0.82));
    }
}