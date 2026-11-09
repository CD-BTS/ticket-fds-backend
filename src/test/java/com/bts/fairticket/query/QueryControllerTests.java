package com.bts.fairticket.query;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.bts.fairticket.common.auth.ApiKeyConfiguration;
import com.bts.fairticket.common.exception.GlobalExceptionHandler;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.admin.controller.AdminUserController;
import com.bts.fairticket.user.controller.UserQueryController;
import com.bts.fairticket.dashboard.controller.DashboardController;
import com.bts.fairticket.user.service.UserQueryService;
import com.bts.fairticket.behavior.service.UserEventQueryService;
import com.bts.fairticket.dashboard.service.DashboardQueryService;
import com.bts.fairticket.user.ApiKeyTestSupport;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserQueryController.class, AdminUserController.class, DashboardController.class})
@Import({ApiKeyConfiguration.class, GlobalExceptionHandler.class})
class QueryControllerTests {
	private static final String SERVICE_KEY = ApiKeyTestSupport.randomKey();
	private static final String ADMIN = ApiKeyTestSupport.randomKey();
	private static final String GLOBAL = ApiKeyTestSupport.randomKey();
	@DynamicPropertySource
	static void keys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () -> ApiKeyTestSupport.configuration(Map.of("a", SERVICE_KEY)));
		registry.add("BTS_ADMIN_API_KEYS", () -> "[{\"hash\":\"" + ApiKeyTestSupport.hash(ADMIN)
				+ "\",\"services\":[\"a\",\"b\"],\"allServices\":false},{\"hash\":\""
				+ ApiKeyTestSupport.hash(GLOBAL) + "\",\"services\":[],\"allServices\":true}]");
	}
	@Autowired MockMvc mvc;
	@MockitoBean UserQueryService users;
	@MockitoBean UserEventQueryService events;
	@MockitoBean DashboardQueryService dashboard;

	@Test
	void authenticationCoversAllNewRoutesAndNormalizesPaths() throws Exception {
		for (String path : List.of("/api/v1/users", "/api/v1/users/id/events", "/api/v1/admin/users",
				"/api/v1/dashboard/summary", "/api/v1/admin/users;x=1", "/api/v1/dashboard/summary;x=1")) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
			mvc.perform(get(path).header("X-API-Key", "wrong")).andExpect(status().isUnauthorized());
			mvc.perform(get(path).header("X-API-Key", SERVICE_KEY, SERVICE_KEY)).andExpect(status().isUnauthorized());
		}
		mvc.perform(get("/context/api/v1/admin/users").contextPath("/context")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", SERVICE_KEY))
				.andExpect(status().isForbidden()).andExpect(jsonPath("code").value("FORBIDDEN"));
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", SERVICE_KEY)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/users").header("X-API-Key", GLOBAL)).andExpect(status().isForbidden());
		verifyNoInteractions(users, events, dashboard);
	}

	@Test
	void derivesServiceScopesFromCredentialsAndRejectsUnauthorizedSelection() throws Exception {
		when(users.find(any(), any())).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
		mvc.perform(get("/api/v1/users").header("X-API-Key", SERVICE_KEY)).andExpect(status().isOk())
				.andExpect(jsonPath("data.content").isEmpty()).andExpect(jsonPath("data.totalElements").value(0));
		verify(users).find(any(), eq(Set.of("a")));
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", ADMIN)).andExpect(status().isOk());
		verify(users).find(any(), eq(Set.of("a", "b")));
		mvc.perform(get("/api/v1/admin/users").param("serviceId", "c").header("X-API-Key", ADMIN)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/dashboard/summary").param("serviceId", "c").header("X-API-Key", ADMIN)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/users").header("X-API-Key", GLOBAL)).andExpect(status().isOk());
		verify(users).find(any(), isNull());
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", ADMIN)).andExpect(status().isOk());
		verify(dashboard).summarize(Set.of("a", "b"));
		mvc.perform(get("/api/v1/dashboard/summary").header("X-API-Key", GLOBAL)).andExpect(status().isOk());
		verify(dashboard).summarize(null);
	}

	@Test
	void invalidTypesAndFiltersUseExistingErrorEnvelope() throws Exception {
		for (Map.Entry<String, String> param : Map.of("page", "abc", "accountStatus", "UNKNOWN", "minCreditScore", "-1",
				"maxCreditScore", "101", "createdFrom", "invalid", "serviceId", " ").entrySet()) {
			mvc.perform(get("/api/v1/admin/users").param(param.getKey(), param.getValue()).header("X-API-Key", ADMIN))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
		}
		mvc.perform(get("/api/v1/users").param("serviceId", "b").header("X-API-Key", SERVICE_KEY)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/id/events").param("eventType", "INVALID").header("X-API-Key", SERVICE_KEY)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/id/events").param("from", "2026-01-01T00:00:00").header("X-API-Key", SERVICE_KEY))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(users, events, dashboard);
	}
}
