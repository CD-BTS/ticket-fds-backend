package com.bts.fairticket.query;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.user.ApiKeyTestSupport;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.service.UserQueryService;
import com.bts.fairticket.dashboard.service.DashboardQueryService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"logging.level.org.hibernate.stat=OFF",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
@Transactional
class QueryApiMySqlTests {
	private static final String A = "query-" + UUID.randomUUID();
	private static final String B = "query-" + UUID.randomUUID();
	private static final String SERVICE_KEY = ApiKeyTestSupport.randomKey();
	private static final String ADMIN = ApiKeyTestSupport.randomKey();
	private static final String GLOBAL = ApiKeyTestSupport.randomKey();
	private final String first = UUID.randomUUID().toString();
	private final String second = UUID.randomUUID().toString();
	private final String foreign = UUID.randomUUID().toString();
	@DynamicPropertySource
	static void keys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () -> ApiKeyTestSupport.configuration(Map.of(A, SERVICE_KEY)));
		registry.add("BTS_ADMIN_API_KEYS", () -> "[{\"hash\":\"" + ApiKeyTestSupport.hash(ADMIN)
				+ "\",\"services\":[\"" + A + "\"],\"allServices\":false},{\"hash\":\""
				+ ApiKeyTestSupport.hash(GLOBAL) + "\",\"services\":[],\"allServices\":true}]");
	}
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;
	@Autowired UserQueryService users;
	@Autowired DashboardQueryService dashboard;
	@Autowired EntityManagerFactory emf;

	@BeforeEach
	void fixture() {
		user(first, A, "first", "ACTIVE", 80, "BASE");
		user(second, A, "second", "SUSPENDED", 40, "TEST");
		user(foreign, B, "foreign", "ACTIVE", 100, "BASE");
		event(first, A, "booking", "BOOKING", "2026-01-01 00:00:00");
		event(first, A, "cancel", "CANCEL", "2026-01-01 00:00:00");
		event(first, A, "checkin", "CHECK_IN", "2026-01-02 00:00:00");
		event(foreign, B, "foreign", "BOOKING", "2026-01-01 00:00:00");
		// Multiple assessments must never multiply user, credit or event aggregates.
		jdbc.update("insert into risk_assessment(user_id,anomaly_score,model_version) values (?,0.3,'test'),(?,0.4,'test')", first, first);
	}

	private void user(String id, String service, String external, String status, int credit, String tier) {
		jdbc.update("insert into user_account(user_id,service_id,external_user_id,account_status,created_at) values (?,?,?,?,'2026-01-01 00:00:00')",
				id, service, external, status);
		jdbc.update("insert into user_credit(user_id,credit_score,tier) values (?,?,?)", id, credit, tier);
	}
	private void event(String user, String service, String external, String type, String time) {
		jdbc.update("insert into behavior_event(user_id,service_id,external_event_id,event_type,occurred_at,metadata) values (?,?,?,?,?,?)",
				user, service, external, type, time, "{\"deviceChanged\":false}");
	}

	@Test
	void userPagesAreScopedStableAndUseTwoQueriesWithoutNPlusOne() throws Exception {
		var stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();
		var page = users.find(new UserQuery(0, 1, "userId,asc", null, null, null, null, null, null, null, null, null), Set.of(A));
		assertEquals(2, page.totalElements());
		assertEquals(2, page.totalPages());
		assertTrue(page.hasNext());
		assertEquals(first.compareTo(second) < 0 ? first : second, page.content().get(0).userId());
		assertEquals(2, stats.getPrepareStatementCount());
		mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY).param("sort", "creditScore,desc"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2))
				.andExpect(jsonPath("data.content[0].userId").value(first));
		mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY).param("page", "10"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty())
				.andExpect(jsonPath("data.totalElements").value(2)).andExpect(jsonPath("data.hasNext").value(false));
	}

	@Test
	void adminCombinesFiltersAndGlobalCredentialCanSelectOtherServices() throws Exception {
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", ADMIN)
				.param("serviceId", A).param("userId", first).param("externalUserId", "first")
				.param("accountStatus", "ACTIVE").param("tier", "BASE")
				.param("minCreditScore", "80").param("maxCreditScore", "80")
				.param("createdFrom", "2026-01-01T00:00:00").param("createdTo", "2026-01-02T00:00:00"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1))
				.andExpect(jsonPath("data.content[0].creditScore").value(80));
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", ADMIN).param("externalUserId", "foreign"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty());
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", GLOBAL).param("serviceId", B))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1))
				.andExpect(jsonPath("data.content[0].userId").value(foreign));
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", GLOBAL))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(
						jdbc.queryForObject("select count(*) from user_account", Integer.class)));
	}

	@Test
	void eventsRespectTypeUtcRangeStableOrderingPaginationAndOwnership() throws Exception {
		mvc.perform(get("/api/v1/users/" + first + "/events").header("X-API-Key", SERVICE_KEY)
				.param("from", "2026-01-01T09:00:00+09:00").param("to", "2026-01-02T00:00:00Z").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2))
				.andExpect(jsonPath("data.content[0].externalEventId").value("cancel"))
				.andExpect(jsonPath("data.content[0].occurredAt").value("2026-01-01T00:00:00Z"))
				.andExpect(jsonPath("data.content[0].metadata.deviceChanged").value(false))
				.andExpect(jsonPath("data.hasNext").value(true));
		mvc.perform(get("/api/v1/users/" + first + "/events").header("X-API-Key", SERVICE_KEY).param("eventType", "BOOKING"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1));
		mvc.perform(get("/api/v1/users/" + first + "/events").header("X-API-Key", SERVICE_KEY)
				.param("sort", "eventId,asc").param("size", "1").param("page", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("data.content[0].externalEventId").value("cancel"));
		mvc.perform(get("/api/v1/users/" + second + "/events").header("X-API-Key", SERVICE_KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty());
		for (String id : new String[]{foreign, UUID.randomUUID().toString()}) {
			mvc.perform(get("/api/v1/users/" + id + "/events").header("X-API-Key", SERVICE_KEY))
					.andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
		}
	}

	@Test
	void summaryCountsActualRowsWithoutJoinMultiplicationAndHandlesEmptyScope() throws Exception {
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", ADMIN))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalUsers").value(2))
				.andExpect(jsonPath("data.activeUsers").value(1)).andExpect(jsonPath("data.suspendedUsers").value(1))
				.andExpect(jsonPath("data.usersWithCredit").value(2)).andExpect(jsonPath("data.averageCreditScore").value(60.0))
				.andExpect(jsonPath("data.usersByTier.BASE").value(1)).andExpect(jsonPath("data.usersByTier.TEST").value(1))
				.andExpect(jsonPath("data.totalEvents").value(3)).andExpect(jsonPath("data.eventsByType.BOOKING").value(1))
				.andExpect(jsonPath("data.eventsByType.CANCEL").value(1)).andExpect(jsonPath("data.eventsByType.CHECK_IN").value(1));
		var both = dashboard.summarize(Set.of(A, B));
		assertEquals(3, both.totalUsers());
		assertEquals(4, both.totalEvents());
		assertEquals(220.0 / 3, both.averageCreditScore(), 0.0001);
		var all = dashboard.summarize(null);
		assertEquals(jdbc.queryForObject("select count(*) from user_account", Long.class), all.totalUsers());
		assertEquals(jdbc.queryForObject("select count(*) from behavior_event", Long.class), all.totalEvents());
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", GLOBAL).param("serviceId", "empty-" + UUID.randomUUID()))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalUsers").value(0))
				.andExpect(jsonPath("data.averageCreditScore").isEmpty()).andExpect(jsonPath("data.eventsByType.BOOKING").value(0));
	}

	@Test
	void scopesAreCaseSensitiveEvenWithMySqlCaseInsensitiveCollation() throws Exception {
		user(UUID.randomUUID().toString(), A.toUpperCase(java.util.Locale.ROOT), "case-other", "ACTIVE", 100, "BASE");
		mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2));
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", ADMIN))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalUsers").value(2));
	}

	@Test
	void missingCreditIsReportedWithoutRepairingDataAndSummaryShowsCoverage() throws Exception {
		jdbc.update("delete from user_credit where user_id = ?", first);
		mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("code").value("INTERNAL_SERVER_ERROR"));
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", ADMIN))
				.andExpect(status().isOk()).andExpect(jsonPath("data.totalUsers").value(2))
				.andExpect(jsonPath("data.usersWithCredit").value(1)).andExpect(jsonPath("data.averageCreditScore").value(40.0));
		assertEquals(0, jdbc.queryForObject("select count(*) from user_credit where user_id = ?", Integer.class, first));
	}

	@Test
	void invalidPaginationSortingAndRangesAreBadRequests() throws Exception {
		for (String[] condition : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "101"},
				{"page", "2147483647"}, {"sort", "unknown,desc"}, {"sort", "createdAt,sideways"},
				{"sort", "createdAt"}, {"sort", "createdAt,desc,other"}}) {
			mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY).param(condition[0], condition[1]))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
		}
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", ADMIN).param("minCreditScore", "90").param("maxCreditScore", "10"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", ADMIN)
				.param("createdFrom", "2026-02-01T00:00:00").param("createdTo", "2026-01-01T00:00:00"))
				.andExpect(status().isBadRequest());
		for (String to : new String[]{"2026-01-01T00:00:00Z", "2025-01-01T00:00:00Z"}) {
			mvc.perform(get("/api/v1/users/" + first + "/events").header("X-API-Key", SERVICE_KEY)
					.param("from", "2026-01-01T00:00:00Z").param("to", to)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/v1/users/" + first + "/events").header("X-API-Key", SERVICE_KEY)
				.param("from", "2026-01-01T00:00:00.001Z")).andExpect(status().isBadRequest());
	}
}
