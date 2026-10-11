package com.bts.fairticket.behavior;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.dto.EventType;
import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.behavior.service.EventService;
import com.bts.fairticket.behavior.service.EventRegistrationTransaction.RegistrationResult;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.ApiKeyTestSupport;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("mysql")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class EventApiMySqlTests {
	private static final String SERVICE_A = "it-" + UUID.randomUUID();
	private static final String SERVICE_B = "it-" + UUID.randomUUID();
	private static final String KEY_A = ApiKeyTestSupport.randomKey();
	private static final String KEY_B = ApiKeyTestSupport.randomKey();

	@DynamicPropertySource
	static void keys(DynamicPropertyRegistry registry) {
		registry.add("BTS_API_KEY_HASHES", () ->
				ApiKeyTestSupport.configuration(Map.of(SERVICE_A, KEY_A, SERVICE_B, KEY_B)));
	}

	@Autowired private MockMvc mvc;
	@Autowired private JsonMapper mapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	@Autowired private UserService userService;
	@Autowired private EventService eventService;
	@MockitoSpyBean private BehaviorEventRepository events;
	private String userId;

	@BeforeEach
	void createOwnUser() {
		userId = userService.register(new UserRegistrationRequest(SERVICE_A,
				UUID.randomUUID().toString()), SERVICE_A).user().userId();
	}

	@AfterEach
	void cleanOnlyOwnRandomServiceFixtures() {
		jdbc.update("DELETE FROM behavior_event WHERE service_id IN (?, ?)", SERVICE_A, SERVICE_B);
		jdbc.update("DELETE FROM user_credit WHERE user_id IN "
				+ "(SELECT user_id FROM user_account WHERE service_id IN (?, ?))", SERVICE_A, SERVICE_B);
		jdbc.update("DELETE FROM user_account WHERE service_id IN (?, ?)", SERVICE_A, SERVICE_B);
		assertEquals(0, eventCount());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE service_id IN (?, ?)",
				Integer.class, SERVICE_A, SERVICE_B));
	}

	private EventRequest request(EventType type, String external) {
		return new EventRequest(SERVICE_A, external, userId, type,
				type == EventType.CHECK_IN ? null : "booking", type == EventType.CHECK_IN ? "ticket" : null,
				"2026-10-10T19:30:00Z", Map.of("device", Map.of("changed", false, "count", 3),
						"steps", List.of("select", "pay"), "duration", 12.5,
						"bookingDurationSeconds", 0.25, "deviceChanged", false,
						"custom_key", Map.of("nested_key", "original")));
	}

	@ParameterizedTest
	@EnumSource(EventType.class)
	void savesAllTypesWithJsonAndExactOccurrenceTime(EventType type) throws Exception {
		var request = request(type, UUID.randomUUID().toString());
		String response = mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A)
				.contentType("application/json").content(mapper.writeValueAsString(request)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON_201"))
				.andExpect(jsonPath("$.data.eventType").value(type.name()))
				.andExpect(jsonPath("$.data.occurredAt").value(request.occurredAt()))
				.andExpect(jsonPath("$.data.metadata.device.changed").value(false))
				.andReturn().getResponse().getContentAsString();
		long id = mapper.readTree(response).get("data").get("eventId").asLong();
		var saved = events.findById(id).orElseThrow();
		assertEquals(request.occurrenceTime(), saved.getOccurredAt());
		assertEquals(request.bookingId(), saved.getBookingId());
		assertEquals(request.ticketId(), saved.getTicketId());
		assertEquals(request.metadata(), saved.getMetadata());
		assertEquals(mapper.readTree(mapper.writeValueAsString(request.metadata())),
				mapper.readTree(jdbc.queryForObject("SELECT metadata FROM behavior_event WHERE event_id=?",
						String.class, id)));
		assertEquals(1, eventCount());
	}

	@Test
	void registeredExternalUserMapsToInternalUuidForAllEventTypes() throws Exception {
		String externalUser = "U0001";
		String registration = mapper.writeValueAsString(Map.of(
				"serviceId", SERVICE_A, "externalUserId", externalUser));
		String json = mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A)
				.contentType("application/json").content(registration))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMMON_201"))
				.andExpect(jsonPath("$.data.externalUserId").value(externalUser))
				.andReturn().getResponse().getContentAsString();
		String internalId = mapper.readTree(json).get("data").get("userId").asString();
		assertEquals(internalId, UUID.fromString(internalId).toString());
		assertNotEquals(externalUser, internalId);
		mvc.perform(post("/api/v1/users").header("X-API-Key", KEY_A)
				.contentType("application/json").content(registration))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("COMMON_200"))
				.andExpect(jsonPath("$.data.userId").value(internalId));
		for (EventType type : EventType.values()) {
			var event = new EventRequest(SERVICE_A, "csv-flow-" + type, internalId, type,
					type == EventType.CHECK_IN ? null : "booking-1",
					type == EventType.CHECK_IN ? "ticket-1" : null,
					"2026-09-13T16:53:52+00:00", Map.of("source", "integration-test"));
			String result = mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A)
					.contentType("application/json").content(mapper.writeValueAsString(event)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.userId").value(internalId))
					.andExpect(jsonPath("$.data.eventType").value(type.name()))
					.andExpect(jsonPath("$.data.occurredAt").value("2026-09-13T16:53:52Z"))
					.andReturn().getResponse().getContentAsString();
			long eventId = mapper.readTree(result).get("data").get("eventId").asLong();
			assertEquals(internalId, jdbc.queryForObject(
					"SELECT user_id FROM behavior_event WHERE event_id=?", String.class, eventId));
		}
		assertEquals(3, eventCount());
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM user_account WHERE service_id=? AND external_user_id=?",
				Integer.class, SERVICE_A, externalUser));
	}

	@Test
	void offsetVariantsReplayTheSameInstantAndStoreUtc() throws Exception {
		String external = UUID.randomUUID().toString();
		Long eventId = null;
		int index = 0;
		for (String time : new String[] {"2026-09-13T16:53:52+00:00", "2026-09-14T01:53:52+09:00",
				"2026-09-13T16:53:52Z", "2026-09-13T16:53:52.000Z"}) {
			boolean created = index++ == 0;
			var request = new EventRequest(SERVICE_A, external, userId, EventType.BOOKING, "booking", null,
					time, Map.of("bookingDurationSeconds", 0.25, "deviceChanged", false,
							"custom", Map.of("nested", List.of(1, 2))));
			String json = mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A)
					.contentType("application/json").content(mapper.writeValueAsString(request)))
					.andExpect(status().is(created ? 201 : 200))
					.andExpect(jsonPath("$.code").value(created ? "COMMON_201" : "COMMON_200"))
					.andExpect(jsonPath("$.data.occurredAt").value("2026-09-13T16:53:52Z"))
					.andExpect(jsonPath("$.data.metadata.custom.nested[1]").value(2))
					.andReturn().getResponse().getContentAsString();
			long id = mapper.readTree(json).get("data").get("eventId").asLong();
			if (eventId == null) {
				eventId = id;
			}
			assertEquals(eventId.longValue(), id);
			assertEquals(java.time.LocalDateTime.of(2026, 9, 13, 16, 53, 52),
					jdbc.queryForObject("SELECT occurred_at FROM behavior_event WHERE event_id=?",
							java.sql.Timestamp.class, id).toLocalDateTime());
		}
		var invalid = new EventRequest(SERVICE_A, external, userId, EventType.BOOKING, "booking", null,
				"2026-09-13T16:53:52", null);
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
				.content(mapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		var changed = new EventRequest(SERVICE_A, external, userId, EventType.BOOKING, "booking", null,
				"2026-09-13T16:53:53Z", Map.of("bookingDurationSeconds", 0.25, "deviceChanged", false,
						"custom", Map.of("nested", List.of(1, 2))));
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
				.content(mapper.writeValueAsString(changed)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
				.andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));
		assertEquals(1, eventCount());
	}

	@Test
	void replayWithReorderedJsonReturnsOriginalIdAndDoesNotUpdate() throws Exception {
		var request = request(EventType.BOOKING, UUID.randomUUID().toString());
		var first = eventService.collect(request, SERVICE_A);
		var metadata = new LinkedHashMap<String, Object>();
		metadata.put("duration", 12.5);
		metadata.put("steps", List.of("select", "pay"));
		metadata.put("device", Map.of("count", 3L, "changed", false));
		metadata.put("bookingDurationSeconds", 0.25);
		metadata.put("deviceChanged", false);
		metadata.put("custom_key", Map.of("nested_key", "original"));
		var replay = new EventRequest(SERVICE_A, request.externalEventId(), userId, EventType.BOOKING,
				"booking", null, request.occurredAt(), metadata);
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
				.content(mapper.writeValueAsString(replay)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_200"))
				.andExpect(jsonPath("$.data.eventId").value(first.event().eventId()));
		assertEquals(1, eventCount());
	}

	@Test
	void invalidMetadataIsRejectedBeforeDatabaseSaveAndOriginalKeysAreReturned() throws Exception {
		for (Map<String, Object> metadata : List.<Map<String, Object>>of(
				Map.of("bookingDurationSeconds", -1), Map.of("bookingDurationSeconds", "3"),
				Map.of("deviceChanged", "false"))) {
			var original = request(EventType.BOOKING, UUID.randomUUID().toString());
			var invalid = new EventRequest(SERVICE_A, original.externalEventId(), userId, EventType.BOOKING,
					"booking", null, original.occurredAt(), metadata);
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
					.content(mapper.writeValueAsString(invalid)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
		}
		assertEquals(0, eventCount());
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
				.content(mapper.writeValueAsString(request(EventType.BOOKING, UUID.randomUUID().toString()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.metadata.custom_key.nested_key").value("original"));
	}

	@Test
	void alteredReplayReturns409AndPreservesOriginalContent() throws Exception {
		var request = request(EventType.BOOKING, UUID.randomUUID().toString());
		var first = eventService.collect(request, SERVICE_A);
		var changed = new EventRequest(SERVICE_A, request.externalEventId(), userId, EventType.CANCEL,
				"booking", null, request.occurredAt(), Map.of("changed", true));
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
				.content(mapper.writeValueAsString(changed)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
				.andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));
		var saved = events.findById(first.event().eventId()).orElseThrow();
		assertEquals("BOOKING", saved.getEventType());
		assertEquals(request.metadata(), saved.getMetadata());
		assertEquals(1, eventCount());
	}

	@Test
	void rejectsMissingUsersOtherServiceUsersAndMismatchedServices() throws Exception {
		String foreign = userService.register(new UserRegistrationRequest(SERVICE_B, "external"), SERVICE_B)
				.user().userId();
		for (String id : new String[] {UUID.randomUUID().toString(), foreign}) {
			var request = new EventRequest(SERVICE_A, UUID.randomUUID().toString(), id, EventType.BOOKING,
					"booking", null, "2026-10-10T19:30:00Z", null);
			mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_A).contentType("application/json")
					.content(mapper.writeValueAsString(request)))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 사용자를 찾을 수 없습니다."));
		}
		mvc.perform(post("/api/v1/events").header("X-API-Key", KEY_B).contentType("application/json")
				.content(mapper.writeValueAsString(request(EventType.BOOKING, "external"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
		assertEquals(0, eventCount());
	}

	@Test
	void rejectsMissingAndInvalidKeysWithoutSaving() throws Exception {
		mvc.perform(post("/api/v1/events").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		mvc.perform(post("/api/v1/events").header("X-API-Key", ApiKeyTestSupport.randomKey())
				.contentType("application/json").content(mapper.writeValueAsString(request(EventType.BOOKING, "external"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."));
		assertEquals(0, eventCount());
	}

	@Test
	void concurrentIdenticalRequestsSaveExactlyOneEvent() throws Exception {
		var request = request(EventType.BOOKING, UUID.randomUUID().toString());
		synchronizeInitialReads(request.externalEventId(), 4);
		var results = concurrent(java.util.Collections.nCopies(4, request));
		var successful = results.stream().map(result -> (RegistrationResult) result).toList();
		assertEquals(1, successful.stream().filter(RegistrationResult::created).count());
		assertEquals(1, successful.stream().map(result -> result.event().eventId()).distinct().count());
		assertEquals(1, eventCount());
	}

	@Test
	void concurrentDifferentContentsHaveOneWinnerAndOneConflict() throws Exception {
		var first = request(EventType.BOOKING, UUID.randomUUID().toString());
		var second = new EventRequest(SERVICE_A, first.externalEventId(), userId, EventType.CANCEL,
				"booking", null, first.occurredAt(), first.metadata());
		synchronizeInitialReads(first.externalEventId(), 2);
		var results = concurrent(List.of(first, second));
		assertEquals(1, results.stream().filter(RegistrationResult.class::isInstance).count());
		assertEquals(1, results.stream().filter(result -> result == ResponseCode.DATA_CONFLICT).count());
		assertEquals(1, eventCount());
	}

	private void synchronizeInitialReads(String external, int workers) {
		var barrier = new CyclicBarrier(workers);
		var reads = new AtomicInteger();
		doAnswer(invocation -> {
			// 실제 DB 조회 결과가 모두 비어 있는 순간을 동기화해 UNIQUE 충돌을 확실하게 발생시킨다.
			var result = entityManager.createQuery(
					"select e from BehaviorEvent e where e.serviceId=:service and e.externalEventId=:external",
					BehaviorEvent.class).setParameter("service", SERVICE_A).setParameter("external", external)
					.getResultStream().findFirst();
			if (reads.incrementAndGet() <= workers) {
				assertTrue(result.isEmpty());
				barrier.await(15, TimeUnit.SECONDS);
			}
			return result;
		}).when(events).findByServiceIdAndExternalEventId(SERVICE_A, external);
	}

	private List<Object> concurrent(List<EventRequest> requests) throws Exception {
		var executor = Executors.newFixedThreadPool(requests.size());
		try {
			List<Callable<Object>> tasks = requests.stream().<Callable<Object>>map(request -> () -> {
				try {
					return eventService.collect(request, SERVICE_A);
				} catch (ApiException exception) {
					return exception.getResponseCode();
				}
			}).toList();
			var results = new ArrayList<Object>();
			for (var future : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
				results.add(future.get(5, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private int eventCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM behavior_event WHERE service_id IN (?, ?)",
				Integer.class, SERVICE_A, SERVICE_B);
	}
}
