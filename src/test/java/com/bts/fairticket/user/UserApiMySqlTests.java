package com.bts.fairticket.user;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.repository.UserAccountRepository;
import com.bts.fairticket.user.service.UserService;
import com.bts.fairticket.user.service.UserRegistrationTransaction.RegistrationResult;

import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("mysql")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class UserApiMySqlTests {

	private static final String SERVICE_A = "it-" + UUID.randomUUID();
	private static final String SERVICE_B = "it-" + UUID.randomUUID();
	private static final String KEY_A = ApiKeyTestSupport.randomKey();
	private static final String KEY_B = ApiKeyTestSupport.randomKey();

	@DynamicPropertySource
	static void configureKeys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () ->
				ApiKeyTestSupport.configuration(Map.of(SERVICE_A, KEY_A, SERVICE_B, KEY_B)));
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private UserService userService;
	@MockitoSpyBean
	private UserAccountRepository users;
	@MockitoSpyBean
	private UserCreditRepository credits;

	@AfterEach
	void removeOnlyThisTestsCommittedFixtures() {
		// REQUIRES_NEW와 병렬 요청은 독립 커밋되므로, 실행별 임의 서비스 ID에 속한 행만 정리한다.
		jdbc.update("DELETE FROM policy_decision WHERE user_id IN "
				+ "(SELECT user_id FROM user_account WHERE service_id IN (?, ?))", SERVICE_A, SERVICE_B);
		jdbc.update("DELETE FROM risk_assessment WHERE user_id IN "
				+ "(SELECT user_id FROM user_account WHERE service_id IN (?, ?))", SERVICE_A, SERVICE_B);
		jdbc.update("DELETE FROM user_credit WHERE user_id IN "
				+ "(SELECT user_id FROM user_account WHERE service_id IN (?, ?))", SERVICE_A, SERVICE_B);
		jdbc.update("DELETE FROM user_account WHERE service_id IN (?, ?)", SERVICE_A, SERVICE_B);
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void registersAccountAndCreditAtomicallyAndRetrievesUser() throws Exception {
		String external = UUID.randomUUID().toString();
		String id = register(external);
		assertEquals(1, fixtureUserCount());
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM user_credit WHERE user_id=?", Integer.class, id));
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_A))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(id))
				.andExpect(jsonPath("$.data.serviceId").value(SERVICE_A))
				.andExpect(jsonPath("$.data.externalUserId").value(external))
				.andExpect(jsonPath("$.data.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.creditScore").value(70))
				.andExpect(jsonPath("$.data.tier").value("BASE"))
				.andExpect(jsonPath("$.data.anomalyScore").hasJsonPath())
				.andExpect(jsonPath("$.data.anomalyScore").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.lastAssessedAt").hasJsonPath())
				.andExpect(jsonPath("$.data.lastAssessedAt").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.latestAction").hasJsonPath())
				.andExpect(jsonPath("$.data.latestAction").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void detailReturnsAssessmentWithoutPolicy() throws Exception {
		String id = register(UUID.randomUUID().toString());
		addAssessment(id, 0.82, "2026-10-10 12:00:00");
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_A))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.anomalyScore").value(0.82))
				.andExpect(jsonPath("$.data.lastAssessedAt").value("2026-10-10T12:00:00"))
				.andExpect(jsonPath("$.data.latestAction").hasJsonPath())
				.andExpect(jsonPath("$.data.latestAction").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void detailReturnsPolicyWithoutAssessment() throws Exception {
		String id = register(UUID.randomUUID().toString());
		addPolicy(id, "DELAY", "2026-10-10 12:00:00");
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_A))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.latestAction").value("DELAY"))
				.andExpect(jsonPath("$.data.anomalyScore").hasJsonPath())
				.andExpect(jsonPath("$.data.anomalyScore").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.lastAssessedAt").hasJsonPath())
				.andExpect(jsonPath("$.data.lastAssessedAt").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void detailSelectsLatestTimestampThenLargestIdIndependentlyForEachHistory() throws Exception {
		String id = register(UUID.randomUUID().toString());
		addAssessment(id, 0.1, "2026-10-10 10:00:00");
		addAssessment(id, 0.2, "2026-10-10 12:00:00");
		addAssessment(id, 0.8, "2026-10-10 12:00:00");
		addAssessment(id, 0.99, "2026-10-10 11:00:00");
		addPolicy(id, "ALLOW", "2026-10-10 10:00:00");
		addPolicy(id, "DELAY", "2026-10-10 12:00:00");
		addPolicy(id, "REQUIRE_CAPTCHA", "2026-10-10 12:00:00");
		addPolicy(id, "RESTRICT", "2026-10-10 11:00:00");
		String foreign = userService.register(new UserRegistrationRequest(SERVICE_B, UUID.randomUUID().toString()), SERVICE_B)
				.user().userId();
		addAssessment(foreign, 1.0, "2099-01-01 00:00:00");
		addPolicy(foreign, "FOREIGN", "2099-01-01 00:00:00");
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_A))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
				.andExpect(jsonPath("$.data.anomalyScore").value(0.8))
				.andExpect(jsonPath("$.data.lastAssessedAt").value("2026-10-10T12:00:00"))
				.andExpect(jsonPath("$.data.latestAction").value("REQUIRE_CAPTCHA"));
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_B))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 사용자를 찾을 수 없습니다."))
				.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
	}

	private void addAssessment(String id, double score, String time) {
		jdbc.update("INSERT INTO risk_assessment (user_id, anomaly_score, model_version, assessed_at) VALUES (?, ?, ?, ?)",
				id, score, "test", time);
	}

	private void addPolicy(String id, String action, String time) {
		jdbc.update("INSERT INTO policy_decision (user_id, context, credit_score_at_decision, action, decided_at) "
				+ "VALUES (?, ?, ?, ?, ?)", id, "BOOKING_ATTEMPT", 70, action, time);
	}

	@Test
	void storesRequestedStatusAndKeepsStatusAndCreditOnReRegistration() throws Exception {
		for (String input : new String[] {"omitted", "ACTIVE", "SUSPENDED", "null"}) {
			String external = UUID.randomUUID().toString();
			var request = new java.util.LinkedHashMap<String, Object>();
			request.put("serviceId", SERVICE_A);
			request.put("externalUserId", external);
			if (!input.equals("omitted")) {
				request.put("accountStatus", input.equals("null") ? null : input);
			}
			String expected = input.equals("SUSPENDED") ? "SUSPENDED" : "ACTIVE";
			String response = mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A)
					.contentType("application/json").content(jsonMapper.writeValueAsString(request)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.data.accountStatus").value(expected))
					.andExpect(jsonPath("$.data.creditScore").value(70))
					.andReturn().getResponse().getContentAsString();
			String id = jsonMapper.readTree(response).get("data").get("userId").asString();
			assertEquals(expected, jdbc.queryForObject("SELECT account_status FROM user_account WHERE user_id=?", String.class, id));
			jdbc.update("UPDATE user_credit SET credit_score=39, tier='KEPT' WHERE user_id=?", id);
			request.put("accountStatus", expected.equals("ACTIVE") ? "SUSPENDED" : "ACTIVE");
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A)
					.contentType("application/json").content(jsonMapper.writeValueAsString(request)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
					.andExpect(jsonPath("$.data.userId").value(id))
					.andExpect(jsonPath("$.data.accountStatus").value(expected))
					.andExpect(jsonPath("$.data.creditScore").value(39))
					.andExpect(jsonPath("$.data.tier").value("KEPT"));
			mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_A))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.accountStatus").value(expected));
		}
	}

	@Test
	void reRegistrationKeepsExistingUserAndCredit() throws Exception {
		String external = UUID.randomUUID().toString();
		String id = register(external);
		jdbc.update("UPDATE user_credit SET credit_score=42, tier='EXISTING' WHERE user_id=?", id);
		jdbc.update("UPDATE user_account SET account_status='SUSPENDED' WHERE user_id=?", id);
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A).contentType("application/json")
					.content(body(SERVICE_A, external)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
					.andExpect(jsonPath("$.data.userId").value(id))
					.andExpect(jsonPath("$.data.accountStatus").value("SUSPENDED"))
					.andExpect(jsonPath("$.data.creditScore").value(42))
					.andExpect(jsonPath("$.data.tier").value("EXISTING"));
		}
		assertEquals(1, fixtureUserCount());
	}

	@Test
	void rejectsDifferentServiceRegistration() throws Exception {
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A).contentType("application/json")
				.content(body(SERVICE_B, "external")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void hidesMissingAndOtherServiceUsers() throws Exception {
		String id = register(UUID.randomUUID().toString());
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_B))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 사용자를 찾을 수 없습니다."));
		mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("X-API-Key", KEY_A))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 사용자를 찾을 수 없습니다."));
	}

	@Test
	void rejectsMissingAndInvalidKeysWithoutCreatingUsers() throws Exception {
		mvc.perform(post("/api/v1/users").contentType("application/json").content(body(SERVICE_A, "external")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(post("/api/v1/users").header("X-API-Key", ApiKeyTestSupport.randomKey())
				.contentType("application/json").content(body(SERVICE_A, "external")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(get("/api/v1/users/missing")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void rollsBackAccountWhenCreditCreationFails() throws Exception {
		String external = UUID.randomUUID().toString();
		doThrow(new DataIntegrityViolationException("test credit creation failure"))
				.when(credits).saveAndFlush(any(UserCredit.class));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A).contentType("application/json")
				.content(body(SERVICE_A, external)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
		assertTrue(users.findByServiceIdAndExternalUserId(SERVICE_A, external).isEmpty());
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void concurrentFirstRegistrationsCreateExactlyOneAccountAndCredit() throws Exception {
		int workers = 4;
		String external = UUID.randomUUID().toString();
		CyclicBarrier initialReads = new CyclicBarrier(workers);
		AtomicInteger reads = new AtomicInteger();
		doAnswer(invocation -> {
			var result = entityManager.createQuery(
                    "select u from UserAccount u where u.serviceId = :service and u.externalUserId = :external",
                    UserAccount.class).setParameter("service", SERVICE_A)
                    .setParameter("external", external).getResultStream().findFirst();
			if (reads.incrementAndGet() <= workers) {
				initialReads.await(15, TimeUnit.SECONDS);
			}
			return result;
		}).when(users).findByServiceIdAndExternalUserId(SERVICE_A, external);

		var executor = Executors.newFixedThreadPool(workers);
		try {
			List<java.util.concurrent.Callable<RegistrationResult>> requests =
					java.util.stream.IntStream.range(0, workers)
							.<java.util.concurrent.Callable<RegistrationResult>>mapToObj(index ->
									() -> userService.register(new UserRegistrationRequest(SERVICE_A, external), SERVICE_A))
							.toList();
			var futures = executor.invokeAll(requests, 30, TimeUnit.SECONDS);
			var results = new java.util.ArrayList<RegistrationResult>();
			for (var future : futures) {
				results.add(future.get(5, TimeUnit.SECONDS));
			}
			assertEquals(1, results.stream().map(result -> result.user().userId()).distinct().count());
			assertEquals(1, results.stream().filter(RegistrationResult::created).count());
			assertEquals(1, fixtureUserCount());
			String id = results.get(0).user().userId();
			assertEquals(1, jdbc.queryForObject(
					"SELECT COUNT(*) FROM user_credit WHERE user_id=?", Integer.class, id));
			assertTrue(results.stream().allMatch(result -> result.user().creditScore() == 70));
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private String register(String external) throws Exception {
		String response = mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A)
				.contentType("application/json").content(body(SERVICE_A, external)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON_201"))
				.andExpect(jsonPath("$.data.anomalyScore").doesNotExist())
				.andExpect(jsonPath("$.data.lastAssessedAt").doesNotExist())
				.andExpect(jsonPath("$.data.latestAction").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("userId").asString();
	}

	private String body(String serviceId, String external) {
		return jsonMapper.writeValueAsString(Map.of("serviceId", serviceId, "externalUserId", external));
	}

	private int fixtureUserCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE service_id IN (?, ?)",
				Integer.class, SERVICE_A, SERVICE_B);
	}
}
