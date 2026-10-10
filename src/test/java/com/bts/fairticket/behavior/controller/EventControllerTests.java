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
		return new LinkedHashMap<>(Map.of("service_id", "service-a", "external_event_id", "external",
				"user_id", "user", "event_type", "BOOKING", "booking_id", "booking",
				"occurred_at", "2026-10-10T19:30:00", "metadata", Map.of("duration", 10)));
	}

	@Test
	void acceptsAllThreeEventTypesAndReturnsCommon201() throws Exception {
		for (String type : new String[] {"BOOKING", "CANCEL", "CHECK_IN"}) {
			var body = body();
			body.put("event_type", type);
			if (type.equals("CHECK_IN")) {
				body.remove("booking_id");
				body.put("ticket_id", "ticket");
			}
			var response = new EventResponse(1L, "service-a", "external", "user", type,
					(String) body.get("booking_id"), (String) body.get("ticket_id"),
					"2026-10-10T19:30:00", Map.of("duration", 10));
			when(service.collect(any(), eq("service-a"))).thenReturn(new RegistrationResult(response, true));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(body)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON_201"))
					.andExpect(jsonPath("$.data.event_type").value(type))
					.andExpect(jsonPath("$.data.occurred_at").value("2026-10-10T19:30:00"));
		}
	}

	@Test
	void returns200ForReplayAndCommonErrorStatuses() throws Exception {
		var response = new EventResponse(1L, "service-a", "external", "user", "BOOKING",
				"booking", null, "2026-10-10T19:30:00", null);
		when(service.collect(any(), eq("service-a"))).thenReturn(new RegistrationResult(response, false));
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content(mapper.writeValueAsString(body())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"));
		for (var code : new ResponseCode[] {ResponseCode.NOT_FOUND, ResponseCode.FORBIDDEN, ResponseCode.CONFLICT}) {
			when(service.collect(any(), eq("service-a"))).thenThrow(new ApiException(code));
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
					.content(mapper.writeValueAsString(body())))
					.andExpect(status().is(code.getStatus().value()))
					.andExpect(jsonPath("$.code").value(code.getCode()));
		}
	}

	@Test
	void rejectsMissingAndBlankIdentifiersForEachType() throws Exception {
		for (String type : new String[] {"BOOKING", "CANCEL", "CHECK_IN"}) {
			for (String value : new String[] {"", " "}) {
				var body = body();
				body.put("event_type", type);
				body.remove("booking_id");
				body.put(type.equals("CHECK_IN") ? "ticket_id" : "booking_id", value);
				badRequest(body);
			}
			var body = body();
			body.put("event_type", type);
			body.remove("booking_id");
			badRequest(body);
		}
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingFieldsInvalidTypesAndLengths() throws Exception {
		for (String field : new String[] {"service_id", "external_event_id", "user_id", "event_type", "occurred_at"}) {
			var body = body();
			body.remove(field);
			badRequest(body);
		}
		for (String field : new String[] {"service_id", "external_event_id", "user_id", "booking_id", "ticket_id"}) {
			var body = body();
			body.put(field, "a".repeat(101));
			badRequest(body);
		}
		var body = body();
		body.put("event_type", "UNKNOWN");
		badRequest(body);
		body = body();
		body.put("metadata", new String[] {"not-an-object"});
		badRequest(body);
		for (Object invalid : new Object[] {0, true, java.util.List.of("BOOKING")}) {
			var invalidBody = body();
			invalidBody.put("event_type", invalid);
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
			body.put("occurred_at", time);
			badRequest(body);
		}
		verifyNoInteractions(service);
	}

	@Test
	void rejectsMissingInvalidMultipleKeysAndProtectsNormalizedPaths() throws Exception {
		for (String path : new String[] {"/api/v1/events", "/api/v1/events;version=1"}) {
			mvc.perform(post(path).contentType("application/json").content("{}"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
		}
		mvc.perform(post("/backend/api/v1/events").contextPath("/backend"))
				.andExpect(status().isUnauthorized());
		for (String key : new String[] {"", ApiKeyTestSupport.randomKey()}) {
			mvc.perform(post("/api/v1/events").header("X-API-Key", key))
					.andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY, KEY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	private void badRequest(Map<String, Object> body) throws Exception {
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
				.content(mapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
	}
}
