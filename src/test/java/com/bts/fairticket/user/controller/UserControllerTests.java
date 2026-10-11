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
import com.bts.fairticket.user.dto.UserDetailResponse;
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
	void servicePermissionAndUnexpectedFailuresMatchContract() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenThrow(new ApiException(ResponseCode.FORBIDDEN));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"serviceId\":\"other-service\",\"externalUserId\":\"external-1\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
				.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
		when(service.getUser("id", SERVICE)).thenThrow(new IllegalStateException("private detail"));
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
				.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void newRegistrationReturns201AndMatchingCode() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenReturn(new RegistrationResult(USER, true));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"serviceId\":\"service-a\",\"externalUserId\":\"external-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("COMMON_201"))
				.andExpect(jsonPath("$.data.userId").value("id"))
				.andExpect(jsonPath("$.data.creditScore").value(70))
				.andExpect(jsonPath("$.data.tier").value("BASE"));
	}

	@Test
	void acceptsDefaultExplicitAndNullAccountStatusesInCamelCaseRequests() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenReturn(new RegistrationResult(USER, true));
		for (String field : new String[] {"", ",\"accountStatus\":\"ACTIVE\"",
				",\"accountStatus\":\"SUSPENDED\"", ",\"accountStatus\":null"}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
					.content("{\"serviceId\":\"service-a\",\"externalUserId\":\"external-1\"" + field + "}"))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.data.userId").value("id"))
					.andExpect(jsonPath("$.data.serviceId").value(SERVICE))
					.andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
					.andExpect(jsonPath("$.data.createdAt").exists())
					.andExpect(jsonPath("$.data.user_id").doesNotExist());
		}
		var captured = org.mockito.ArgumentCaptor.forClass(com.bts.fairticket.user.dto.UserRegistrationRequest.class);
		org.mockito.Mockito.verify(service, org.mockito.Mockito.times(4)).register(captured.capture(), eq(SERVICE));
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("ACTIVE", "ACTIVE", "SUSPENDED", "ACTIVE"),
				captured.getAllValues().stream().map(com.bts.fairticket.user.dto.UserRegistrationRequest::accountStatus).toList());
	}

	@Test
	void rejectsInvalidAccountStatusesAndLegacySnakeCaseRequests() throws Exception {
		for (String value : new String[] {"\"UNKNOWN\"", "\"active\"", "\"\"", "\" \"", "0", "true"}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
					.content("{\"serviceId\":\"service-a\",\"externalUserId\":\"external-1\",\"accountStatus\":" + value + "}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		}
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"service_id\":\"service-a\",\"external_user_id\":\"external-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		verifyNoInteractions(service);
	}

	@Test
	void duplicateRegistrationReturns200() throws Exception {
		when(service.register(any(), eq(SERVICE))).thenReturn(new RegistrationResult(USER, false));
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"serviceId\":\"service-a\",\"externalUserId\":\"external-1\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"));
	}

	@Test
	void getUserReturnsDto() throws Exception {
		when(service.getUser("id", SERVICE)).thenReturn(UserDetailResponse.from(USER, null, null));
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.externalUserId").value("external-1"))
				.andExpect(jsonPath("$.data.anomalyScore").hasJsonPath())
				.andExpect(jsonPath("$.data.anomalyScore").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.lastAssessedAt").hasJsonPath())
				.andExpect(jsonPath("$.data.lastAssessedAt").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.latestAction").hasJsonPath())
				.andExpect(jsonPath("$.data.latestAction").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void detailSerializesStoredHistoryWithExistingLocalDateTimePolicy() throws Exception {
		when(service.getUser("id", SERVICE)).thenReturn(new UserDetailResponse("id", SERVICE, "external-1",
				"ACTIVE", USER.createdAt(), 70, "BASE", 0.82, LocalDateTime.of(2026, 10, 10, 12, 0), "DELAY"));
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
				.andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
				.andExpect(jsonPath("$.data.anomalyScore").value(0.82))
				.andExpect(jsonPath("$.data.lastAssessedAt").value("2026-10-10T12:00:00"))
				.andExpect(jsonPath("$.data.latestAction").value("DELAY"));
	}

	@Test
	void missingUserReturnsUserNotFound() throws Exception {
		when(service.getUser("missing", SERVICE)).thenThrow(new ApiException(ResponseCode.USER_NOT_FOUND));
		mvc.perform(get("/api/v1/users/missing").header("X-API-Key", KEY))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 사용자를 찾을 수 없습니다."));
	}

	@Test
	void bothEndpointsRejectMissingInvalidAndMultipleKeys() throws Exception {
		for (String key : new String[] {"", ApiKeyTestSupport.randomKey()}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", key).contentType("application/json").content("{}"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
			mvc.perform(get("/api/v1/users/id").header("X-API-Key", key))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
		}
		mvc.perform(get("/api/v1/users/id")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(post("/api/v1/users").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(get("/api/v1/users/id").header("X-API-Key", KEY, KEY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingBlankAndOversizedFields() throws Exception {
		for (String body : new String[] {"{}", "{\"serviceId\":\"service-a\"}",
				"{\"serviceId\":\" \",\"externalUserId\":\"external\"}",
				"{\"serviceId\":\"service-a\",\"externalUserId\":\"\"}",
				"{\"serviceId\":\"" + "a".repeat(51) + "\",\"externalUserId\":\"external\"}",
				"{\"serviceId\":\"service-a\",\"externalUserId\":\"" + "a".repeat(101) + "\"}"}) {
			mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json").content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		}
		verifyNoInteractions(service);
	}

	@Test
	void authenticationAlsoProtectsContextPathsAndMatrixParameters() throws Exception {
		mvc.perform(get("/backend/api/v1/users/id").contextPath("/backend"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(get("/api/v1/users;version=1/id"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		verifyNoInteractions(service);
	}
	@Test
	void rejectsMalformedJsonAndOversizedUserId() throws Exception {
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY).contentType("application/json").content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		mvc.perform(get("/api/v1/users/" + "a".repeat(51)).header("X-API-Key", KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		verifyNoInteractions(service);
	}
}
