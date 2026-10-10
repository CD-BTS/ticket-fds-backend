package com.bts.fairticket.user.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bts.fairticket.common.auth.ApiKeyConfiguration;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.exception.GlobalExceptionHandler;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.ApiKeyTestSupport;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.service.UserService;
import com.bts.fairticket.user.service.UserRegistrationTransaction.RegistrationResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({ApiKeyConfiguration.class, GlobalExceptionHandler.class})
class UserControllerTests {

	private static final String KEY = ApiKeyTestSupport.randomKey();
	private static final String SERVICE = "service-a";
	private static final UserResponse USER = new UserResponse("id", SERVICE, "external-1",
			"ACTIVE", LocalDateTime.of(2026, 1, 1, 0, 0), 70, "BASE");

	@DynamicPropertySource
	static void configureKeys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () -> ApiKeyTestSupport.configuration(Map.of(SERVICE, KEY)));
	}

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private UserService service;

	@Test
	void newRegistrationReturns201AndMatchingCode() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenReturn(new RegistrationResult(USER, true));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"service_id\":\"service-a\",\"external_user_id\":\"external-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("COMMON_201"))
				.andExpect(jsonPath("$.data.user_id").value("id"))
				.andExpect(jsonPath("$.data.credit_score").value(70))
				.andExpect(jsonPath("$.data.tier").value("BASE"));
	}

	@Test
	void duplicateRegistrationReturns200() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenReturn(new RegistrationResult(USER, false));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"service_id\":\"service-a\",\"external_user_id\":\"external-1\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"));
	}

	@Test
	void getUserReturnsDto() throws Exception {
		when(service.getUser("id", SERVICE)).thenReturn(USER);
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.external_user_id").value("external-1"));
	}

	@Test
	void missingUserReturnsCommon404() throws Exception {
		when(service.getUser("missing", SERVICE)).thenThrow(new ApiException(ResponseCode.NOT_FOUND));
		mvc.perform(get("/api/v1/users/missing").header("X-API-Key", KEY))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON_404"));
	}

	@Test
	void bothEndpointsRejectMissingInvalidAndMultipleKeys() throws Exception {
		for (String key : new String[] {"", ApiKeyTestSupport.randomKey()}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", key).contentType("application/json").content("{}"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
			mvc.perform(get("/api/v1/users/id").header("X-API-Key", key))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
		}
		mvc.perform(get("/api/v1/users/id")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/users").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY, KEY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingBlankAndOversizedFields() throws Exception {
		for (String body : new String[] {"{}", "{\"service_id\":\"service-a\"}",
				"{\"service_id\":\" \",\"external_user_id\":\"external\"}",
				"{\"service_id\":\"service-a\",\"external_user_id\":\"\"}",
				"{\"service_id\":\"" + "a".repeat(51) + "\",\"external_user_id\":\"external\"}",
				"{\"service_id\":\"service-a\",\"external_user_id\":\"" + "a".repeat(101) + "\"}"}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json").content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
		}
		verifyNoInteractions(service);
	}

	@Test
	void authenticationAlsoProtectsContextPathsAndMatrixParameters() throws Exception {
		mvc.perform(get("/backend/api/v1/users/id").contextPath("/backend"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users;version=1/id"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
	@Test
	void rejectsMalformedJsonAndOversizedUserId() throws Exception {
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json").content("{"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/" + "a".repeat(51)).header("X-API-Key", KEY))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}
}
