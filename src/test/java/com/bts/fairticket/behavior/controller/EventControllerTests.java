package com.bts.fairticket.behavior.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.service.EventService;
import com.bts.fairticket.behavior.service.EventRegistrationTransaction.RegistrationResult;
import com.bts.fairticket.common.auth.ApiKeyConfiguration;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.exception.GlobalExceptionHandler;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.ApiKeyTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({ApiKeyConfiguration.class, GlobalExceptionHandler.class})
class EventControllerTests {
	private static final String KEY = ApiKeyTestSupport.randomKey();

	@DynamicPropertySource
	static void keys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () -> ApiKeyTestSupport.configuration(Map.of("service-a", KEY)));
	}

	@Autowired private MockMvc mvc;
	@Autowired private JsonMapper mapper;
	@MockitoBean private EventService service;

	private Map<String, Object> body() {
		return new LinkedHashMap<>(Map.of("serviceId", "service-a", "externalEventId", "external",
				"userId", "user", "eventType", "BOOKING", "bookingId", "booking",
				"occurredAt", "2026-10-10T19:30:00", "metadata", Map.of("duration", 10)));
	}

	@Test
	void domainErrorsAndUnexpectedFailuresMatchLiteralContract() throws Exception {
		Object[][] errors = {
				{ResponseCode.USER_NOT_FOUND, 404, "USER_NOT_FOUND", "해당 사용자를 찾을 수 없습니다."},
				{ResponseCode.FORBIDDEN, 403, "FORBIDDEN", "접근 권한이 없습니다."},
				{ResponseCode.EVENT_ALREADY_EXISTS, 409, "EVENT_ALREADY_EXISTS", "동일한 이벤트 식별자로 서로 다른 내용이 요청되었습니다."}
		};
		for (Object[] error : errors) {
			when(service.collect(any(), eq("service-a"))).thenThrow(new ApiException((ResponseCode) error[0]));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(body())))
					.andExpect(status().is((Integer) error[1]))
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value(error[2]))
					.andExpect(jsonPath("$.message").value(error[3]))
					.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
		}
		when(service.collect(any(), eq("service-a"))).thenThrow(new IllegalStateException("private detail"));
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content(mapper.writeValueAsString(body())))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
				.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void acceptsAllThreeEventTypesAndReturnsCommon201() throws Exception {
		for (String type : new String[] {"BOOKING", "CANCEL", "CHECK_IN"}) {
			var body = body();
			body.put("eventType", type);
			if (type.equals("CHECK_IN")) {
				body.remove("bookingId");
				body.put("ticketId", "ticket");
			}
			var response = new EventResponse(1L, "service-a", "external", "user", type,
					(String) body.get("bookingId"), (String) body.get("ticketId"),
					"2026-10-10T19:30:00", Map.of("duration", 10));
			when(service.collect(any(), eq("service-a"))).thenReturn(new RegistrationResult(response, true));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(body)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON_201"))
					.andExpect(jsonPath("$.data.eventType").value(type))
					.andExpect(jsonPath("$.data.occurredAt").value("2026-10-10T19:30:00"));
		}
	}

	@Test
	void rejectsInvalidKnownMetadataValuesWithoutRenamingUnknownKeys() throws Exception {
		for (String field : new String[] {"bookingDurationSeconds", "deviceChanged"}) {
			for (Object value : new Object[] {"1", java.util.List.of(1), Map.of("value", 1)}) {
				var request = body();
				request.put("metadata", Map.of(field, value));
				badRequest(request);
			}
			var request = body();
			var metadata = new LinkedHashMap<String, Object>();
			metadata.put(field, null);
			request.put("metadata", metadata);
			badRequest(request);
		}
		for (var metadata : java.util.List.of(Map.of("bookingDurationSeconds", -0.01),
				Map.of("bookingDurationSeconds", true), Map.of("deviceChanged", 1))) {
			var request = body();
			request.put("metadata", metadata);
			badRequest(request);
		}
		verifyNoInteractions(service);
	}

	@Test
	void acceptsOptionalMetadataAndPreservesOriginalKeysAndValueTypes() throws Exception {
		for (Map<String, Object> metadata : java.util.List.<Map<String, Object>>of(Map.of(),
				Map.of("bookingDurationSeconds", 0, "deviceChanged", false, "custom_key", Map.of("nested_key", "original")),
				Map.of("bookingDurationSeconds", 0.25, "deviceChanged", true))) {
			var request = body();
			request.put("metadata", metadata);
			var response = new EventResponse(1L, "service-a", "external", "user", "BOOKING", "booking", null,
					"2026-10-10T19:30:00", metadata);
			when(service.collect(any(), eq("service-a"))).thenReturn(new RegistrationResult(response, true));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(request)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.data.eventId").value(1))
					.andExpect(jsonPath("$.data.event_id").doesNotExist());
		}
		var captured = org.mockito.ArgumentCaptor.forClass(com.bts.fairticket.behavior.dto.EventRequest.class);
		org.mockito.Mockito.verify(service, org.mockito.Mockito.times(3)).collect(captured.capture(), eq("service-a"));
		org.junit.jupiter.api.Assertions.assertEquals(Map.of("nested_key", "original"),
				captured.getAllValues().get(1).metadata().get("custom_key"));
	}

	@Test
	void rejectsLegacySnakeCaseEventRequests() throws Exception {
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content("{\"service_id\":\"service-a\",\"external_event_id\":\"external\",\"user_id\":\"user\","
						+ "\"event_type\":\"BOOKING\",\"booking_id\":\"booking\",\"occurred_at\":\"2026-10-10T19:30:00\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		verifyNoInteractions(service);
	}

	@Test
	void returns200ForReplayAndCommonErrorStatuses() throws Exception {
		var response = new EventResponse(1L, "service-a", "external", "user", "BOOKING",
				"booking", null, "2026-10-10T19:30:00", null);
		when(service.collect(any(), eq("service-a"))).thenReturn(new RegistrationResult(response, false));
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content(mapper.writeValueAsString(body())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"));
		for (var code : new ResponseCode[] {ResponseCode.USER_NOT_FOUND, ResponseCode.FORBIDDEN,
				ResponseCode.EVENT_ALREADY_EXISTS, ResponseCode.DATA_CONFLICT}) {
			when(service.collect(any(), eq("service-a"))).thenThrow(new ApiException(code));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(body())))
					.andExpect(status().is(code.getStatus().value()))
					.andExpect(jsonPath("$.code").value(code.getCode()))
					.andExpect(jsonPath("$.message").value(code.getMessage()));
		}
	}

	@Test
	void rejectsMissingAndBlankIdentifiersForEachType() throws Exception {
		for (String type : new String[] {"BOOKING", "CANCEL", "CHECK_IN"}) {
			for (String value : new String[] {"", " "}) {
				var body = body();
				body.put("eventType", type);
				body.remove("bookingId");
				body.put(type.equals("CHECK_IN") ? "ticketId" : "bookingId", value);
				badRequest(body);
			}
			var body = body();
			body.put("eventType", type);
			body.remove("bookingId");
			badRequest(body);
		}
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingFieldsInvalidTypesAndLengths() throws Exception {
		for (String field : new String[] {"serviceId", "externalEventId", "userId", "eventType", "occurredAt"}) {
			var body = body();
			body.remove(field);
			badRequest(body);
		}
		for (String field : new String[] {"serviceId", "externalEventId", "userId", "bookingId", "ticketId"}) {
			var body = body();
			body.put(field, "a".repeat(101));
			badRequest(body);
		}
		var body = body();
		body.put("eventType", "UNKNOWN");
		badRequest(body);
		body = body();
		body.put("metadata", new String[] {"not-an-object"});
		badRequest(body);
		for (Object invalid : new Object[] {0, true, java.util.List.of("BOOKING")}) {
			var invalidBody = body();
			invalidBody.put("eventType", invalid);
			badRequest(invalidBody);
		}
		verifyNoInteractions(service);
	}

	@Test
	void rejectsFractionalSecondsOffsetsAndInvalidCalendarDates() throws Exception {
		for (String time : new String[] {"2026-10-10T19:30:00.000", "2026-10-10T19:30:00Z",
				"2026-10-10T19:30:00+09:00", "2026-02-30T19:30:00", "2026-10-10T24:00:00",
				"2026-10-10T19:30", "0999-10-10T19:30:00"}) {
			var body = body();
			body.put("occurredAt", time);
			badRequest(body);
		}
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingInvalidMultipleKeysAndProtectsNormalizedPaths() throws Exception {
		for (String path : new String[] {"/api/v1/events", "/api/v1/events;version=1"}) {
			mvc.perform(post(path).contentType("application/json").content("{}"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		}
		mvc.perform(post("/backend/api/v1/events").contextPath("/backend"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		for (String key : new String[] {"", ApiKeyTestSupport.randomKey()}) {
			mvc.perform(post("/api/v1/events").header("X-API-Key", key))
					.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		}
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY, KEY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		verifyNoInteractions(service);
	}

	private void badRequest(Map<String, Object> body) throws Exception {
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content(mapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
	}
}
