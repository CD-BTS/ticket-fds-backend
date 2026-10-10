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
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.user_id").value(id))
				.andExpect(jsonPath("$.data.service_id").value(SERVICE_A))
				.andExpect(jsonPath("$.data.external_user_id").value(external))
				.andExpect(jsonPath("$.data.created_at").isNotEmpty())
				.andExpect(jsonPath("$.data.credit_score").value(70))
				.andExpect(jsonPath("$.data.tier").value("BASE"));
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
					.andExpect(jsonPath("$.data.user_id").value(id))
					.andExpect(jsonPath("$.data.account_status").value("SUSPENDED"))
					.andExpect(jsonPath("$.data.credit_score").value(42))
					.andExpect(jsonPath("$.data.tier").value("EXISTING"));
		}
		assertEquals(1, fixtureUserCount());
	}

	@Test
	void rejectsDifferentServiceRegistration() throws Exception {
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A).contentType("application/json")
				.content(body(SERVICE_B, "external")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON_403"));
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void hidesMissingAndOtherServiceUsers() throws Exception {
		String id = register(UUID.randomUUID().toString());
		mvc.perform(get("/api/v1/users/" + id).header("X-API-Key", KEY_B))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON_404"));
		mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("X-API-Key", KEY_A))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON_404"));
	}

	@Test
	void rejectsMissingAndInvalidKeysWithoutCreatingUsers() throws Exception {
		mvc.perform(post("/api/v1/users").contentType("application/json").content(body(SERVICE_A, "external")))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/users").header("X-API-Key", ApiKeyTestSupport.randomKey())
				.contentType("application/json").content(body(SERVICE_A, "external")))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/missing")).andExpect(status().isUnauthorized());
		assertEquals(0, fixtureUserCount());
	}

	@Test
	void rollsBackAccountWhenCreditCreationFails() throws Exception {
		String external = UUID.randomUUID().toString();
		doThrow(new DataIntegrityViolationException("test credit creation failure"))
				.when(credits).saveAndFlush(any(UserCredit.class));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A).contentType("application/json")
				.content(body(SERVICE_A, external)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMON_409"));
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
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("user_id").asString();
	}

	private String body(String serviceId, String external) {
		return jsonMapper.writeValueAsString(Map.of("service_id", serviceId, "external_user_id", external));
	}

	private int fixtureUserCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE service_id IN (?, ?)",
				Integer.class, SERVICE_A, SERVICE_B);
	}
}
