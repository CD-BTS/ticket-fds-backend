package com.bts.fairticket.credit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("mysql")
class CreditApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private CreditHistoryRepository creditHistoryRepository;

    private static final String ADMIN_TOKEN = "Bearer admin-test-key";

    @Test
    @DisplayName("[통합] 실제 데이터 적재 후 GET /credit 호출 시 올바른 응답을 반환한다")
    void getCredit_E2E() throws Exception {
        UserAccount user = new UserAccount("credit-e2e-user-1", "service-1", "ext-user-1");
        userAccountRepository.save(user);
        userCreditRepository.save(new UserCredit(user));

        mockMvc.perform(get("/api/v1/users/{userId}/credit", user.getUserId())
                        .header("Authorization", ADMIN_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.data.creditScore").value(70))
                .andExpect(jsonPath("$.data.tier").value("BASE"));
    }

    @Test
    @DisplayName("[통합] CreditHistory 적재 후 페이징 조회 검증")
    void getCreditHistory_E2E() throws Exception {
        UserAccount user = new UserAccount("credit-e2e-user-2", "service-1", "ext-user-2");
        userAccountRepository.save(user);
        creditHistoryRepository.save(new CreditHistory(user, null, 70, 60, "예매 취소 페널티"));
        creditHistoryRepository.save(new CreditHistory(user, null, 60, 50, "추가 취소 페널티"));

        mockMvc.perform(get("/api/v1/users/{userId}/credit-history", user.getUserId())
                        .header("Authorization", ADMIN_TOKEN)
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.histories.length()").value(2));
    }
}
