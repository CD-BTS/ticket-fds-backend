package com.bts.fairticket.behavior.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.dto.EventType;
import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTests {
	@Mock private BehaviorEventRepository events;
	@Mock private UserAccountRepository users;
	@Mock private EventRegistrationTransaction transaction;

	private EventRequest request(EventType type) {
		return new EventRequest("service", "external", "user", type,
				type == EventType.CHECK_IN ? null : "booking",
				type == EventType.CHECK_IN ? "ticket" : null,
				"2026-10-10T19:30:00Z", Map.of("object", Map.of("a", 1, "b", 2)));
	}

	private BehaviorEvent event(EventRequest request) {
		return new BehaviorEvent(new UserAccount("user", "service", "external-user"),
				request.serviceId(), request.externalEventId(), request.eventType().name(),
				request.bookingId(), request.ticketId(), request.occurrenceTime(), request.metadata());
	}

	@ParameterizedTest
	@EnumSource(EventType.class)
	void savesEachTypeWithOriginalTimeAndMetadata(EventType type) {
		var request = request(type);
		when(users.findById("user")).thenReturn(Optional.of(new UserAccount("user", "service", "external-user")));
		when(events.findByServiceIdAndExternalEventId("service", "external")).thenReturn(Optional.empty());
		when(events.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		var result = new EventRegistrationTransaction(events, users).register(request);
		assertTrue(result.created());
		assertEquals(type.name(), result.event().eventType());
		assertEquals(request.occurredAt(), result.event().occurredAt());
		assertEquals(request.metadata(), result.event().metadata());
	}

	@Test
	void offsetsNormalizeToUtcAndReplayWithoutSaving() {
		var utc = new EventRequest("service", "external", "user", EventType.BOOKING, "booking", null,
				"2026-09-13T16:53:52+00:00", Map.of());
		var seoul = new EventRequest("service", "external", "user", EventType.BOOKING, "booking", null,
				"2026-09-14T01:53:52+09:00", Map.of());
		assertTrue(utc.isOccurrenceTimeValid());
		assertTrue(seoul.isOccurrenceTimeValid());
		assertEquals(utc.occurrenceTime(), seoul.occurrenceTime());
		assertEquals(LocalDateTime.of(2026, 9, 13, 16, 53, 52), utc.occurrenceTime());
		when(events.findByServiceIdAndExternalEventId("service", "external")).thenReturn(Optional.of(event(utc)));
		var replay = new EventRegistrationTransaction(events, users).findExisting(seoul).orElseThrow();
		assertFalse(replay.created());
		assertEquals("2026-09-13T16:53:52Z", replay.event().occurredAt());
		verify(events, never()).saveAndFlush(any());
	}

	@Test
	void missingAndOtherServiceUsersAreNotFoundWithoutEventAccess() {
		for (Optional<UserAccount> user : java.util.List.of(Optional.<UserAccount>empty(),
				Optional.of(new UserAccount("user", "other", "external-user")))) {
			when(users.findById("user")).thenReturn(user);
			var error = assertThrows(ApiException.class,
					() -> new EventRegistrationTransaction(events, users).register(request(EventType.BOOKING)));
			assertEquals(ResponseCode.USER_NOT_FOUND, error.getResponseCode());
		}
		verifyNoInteractions(events);
	}

	@Test
	void serviceMismatchIsForbiddenBeforeStartingTransaction() {
		var error = assertThrows(ApiException.class,
				() -> new EventService(transaction).collect(request(EventType.BOOKING), "other"));
		assertEquals(ResponseCode.FORBIDDEN, error.getResponseCode());
		verifyNoInteractions(transaction);
	}

	@Test
	void replayIgnoresNestedObjectOrderAndNumberJavaType() {
		var request = request(EventType.BOOKING);
		var saved = event(new EventRequest("service", "external", "user", EventType.BOOKING, "booking", null,
				request.occurredAt(), Map.of("object", Map.of("b", 2L, "a", 1.0))));
		when(events.findByServiceIdAndExternalEventId("service", "external")).thenReturn(Optional.of(saved));
		assertFalse(new EventRegistrationTransaction(events, users).findExisting(request).orElseThrow().created());
		verify(events, never()).saveAndFlush(any());
	}

	@Test
	void replayUsesResolvedUserIdWhenDatabaseReturnsTheSamePrimaryKey() {
		var request = request(EventType.BOOKING);
		var alias = new EventRequest("service", "external", "USER", EventType.BOOKING,
				"booking", null, request.occurredAt(), request.metadata());
		when(events.findByServiceIdAndExternalEventId("service", "external"))
				.thenReturn(Optional.of(event(request)));
		when(users.findById("USER")).thenReturn(Optional.of(new UserAccount("user", "service", "external-user")));
		assertFalse(new EventRegistrationTransaction(events, users).findExisting(alias).orElseThrow().created());
		verify(events, never()).saveAndFlush(any());
	}

	@Test
	void everyChangedContentFieldConflictsAndDoesNotOverwrite() {
		var request = request(EventType.BOOKING);
		var user = new UserAccount("user", "service", "external-user");
		var changed = Stream.of(
				new BehaviorEvent(new UserAccount("other", "service", "x"), "service", "external",
						"BOOKING", "booking", null, request.occurrenceTime(), request.metadata()),
				new BehaviorEvent(user, "service", "external", "CANCEL", "booking", null, request.occurrenceTime(), request.metadata()),
				new BehaviorEvent(user, "service", "external", "BOOKING", "other", null, request.occurrenceTime(), request.metadata()),
				new BehaviorEvent(user, "service", "external", "BOOKING", "booking", "ticket", request.occurrenceTime(), request.metadata()),
				new BehaviorEvent(user, "service", "external", "BOOKING", "booking", null, LocalDateTime.now(), request.metadata()),
				new BehaviorEvent(user, "service", "external", "BOOKING", "booking", null, request.occurrenceTime(), Map.of("changed", true)));
		changed.forEach(saved -> {
			when(events.findByServiceIdAndExternalEventId("service", "external")).thenReturn(Optional.of(saved));
			assertEquals(ResponseCode.DATA_CONFLICT, assertThrows(ApiException.class,
					() -> new EventRegistrationTransaction(events, users).findExisting(request)).getResponseCode());
		});
		verify(events, never()).saveAndFlush(any());
	}

	@Test
	void recoversUniqueCollisionOnlyAfterTransactionFailure() {
		var request = request(EventType.BOOKING);
		var result = new EventRegistrationTransaction.RegistrationResult(
				com.bts.fairticket.behavior.dto.EventResponse.from(event(request)), false);
		when(transaction.register(request)).thenThrow(new DataIntegrityViolationException("test collision"));
		when(transaction.findExisting(request)).thenReturn(Optional.of(result));
		assertSame(result, new EventService(transaction).collect(request, "service"));
		var order = inOrder(transaction);
		order.verify(transaction).register(request);
		order.verify(transaction).findExisting(request);
	}

	@Test
	void unresolvedDatabaseConstraintBecomes409() {
		var request = request(EventType.BOOKING);
		when(transaction.register(request)).thenThrow(new DataIntegrityViolationException("test constraint"));
		when(transaction.findExisting(request)).thenReturn(Optional.empty());
		assertEquals(ResponseCode.DATA_CONFLICT, assertThrows(ApiException.class,
				() -> new EventService(transaction).collect(request, "service")).getResponseCode());
	}
}
