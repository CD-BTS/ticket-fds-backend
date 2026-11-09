package com.bts.fairticket.credit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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

import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.credit.entity.BookingHistory;
import com.bts.fairticket.credit.entity.BookingHistory.BookingStatus;
import com.bts.fairticket.credit.entity.UserCard;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.BookingHistoryRepository;
import com.bts.fairticket.credit.repository.UserCardRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@Tag("mysql")
@SpringBootTest
@Transactional
class CreditEvaluationApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserCreditRepository userCreditRepository;

    @Autowired
    private BookingHistoryRepository bookingHistoryRepository;

    @Autowired
    private UserCardRepository userCardRepository;

    private static final String SERVICE_ID = "service-1";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("[통합] 관람 10회 완료 유저에 대해 /credit/evaluate 호출 시 점수가 75점(NORMAL)으로 갱신된다")
    void evaluateCredit_GoodBookingHistory() throws Exception {
        // Given: 유저 및 초기 크레딧 생성
        UserAccount user = new UserAccount("eval-user-1", SERVICE_ID, "ext-eval-1");
        UserAccount savedUser = userAccountRepository.saveAndFlush(user);
        userCreditRepository.saveAndFlush(new UserCredit(savedUser));

        // 관람 이력 10회 완료 적재
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 10; i++) {
            BookingHistory booking = new BookingHistory(
                    savedUser, "REF-" + i, "EVENT-1",
                    BookingStatus.COMPLETED, now.minusDays(i), now.minusDays(i)
            );
            bookingHistoryRepository.save(booking);
        }
        bookingHistoryRepository.flush();

        String jsonBody = """
            {
                "triggerReason": "정기 재평가",
                "recentIpAttemptCount": 1,
                "intervalVariance": 2.5
            }
        """;

        // When & Then
        mockMvc.perform(post("/api/v1/users/{userId}/credit/evaluate", savedUser.getUserId())
                        .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, SERVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(savedUser.getUserId()))
                .andExpect(jsonPath("$.data.newScore").value(75))
                .andExpect(jsonPath("$.data.grade").value("NORMAL"))
                .andExpect(jsonPath("$.data.penaltyPolicy").value("일반 예매 및 대기열 입장"));
    }

    @Test
    @DisplayName("[통합] 4개 이상 계정에서 공유된 카드 등록 유저 평가 시 15점 감점 반영 확인")
    void evaluateCredit_CardSharingPenalty() throws Exception {
        // Given: 4명의 유저가 동일 카드 해시 공유
        String sharedCardHash = "sha256-shared-card-hash-test";
        UserAccount mainUser = userAccountRepository.saveAndFlush(new UserAccount("card-user-main", SERVICE_ID, "ext-m"));
        userCreditRepository.saveAndFlush(new UserCredit(mainUser));

        userCardRepository.saveAndFlush(new UserCard(mainUser, sharedCardHash, "국민", "1111-****-****-1111"));

        for (int i = 1; i <= 3; i++) {
            UserAccount other = userAccountRepository.saveAndFlush(new UserAccount("card-user-" + i, SERVICE_ID, "ext-" + i));
            userCardRepository.saveAndFlush(new UserCard(other, sharedCardHash, "국민", "1111-****-****-1111"));
        }

        String jsonBody = "{}";

        // When & Then: 신규 베이스(50점) - 카드 공유(15점) = 35점 (SUSPICIOUS)
        mockMvc.perform(post("/api/v1/users/{userId}/credit/evaluate", mainUser.getUserId())
                        .requestAttr(ApiKeyFilter.SERVICE_ATTRIBUTE, SERVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newScore").value(35))
                .andExpect(jsonPath("$.data.grade").value("SUSPICIOUS"))
                .andExpect(jsonPath("$.data.penaltyPolicy").value("CAPTCHA 추가 인증"));
    }
}