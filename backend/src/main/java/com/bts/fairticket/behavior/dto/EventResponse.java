package com.bts.fairticket.behavior.dto;

import java.time.ZoneOffset;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.bts.fairticket.behavior.entity.BehaviorEvent;

public record EventResponse(
		@JsonProperty("eventId") Long eventId,
		@JsonProperty("serviceId") String serviceId,
		@JsonProperty("externalEventId") String externalEventId,
		@JsonProperty("userId") String userId,
		@JsonProperty("eventType") String eventType,
		@JsonProperty("bookingId") String bookingId,
		@JsonProperty("ticketId") String ticketId,
		@JsonProperty("occurredAt") String occurredAt,
		Map<String, Object> metadata) {

	public static EventResponse from(BehaviorEvent event) {
		return new EventResponse(event.getEventId(), event.getServiceId(), event.getExternalEventId(),
				event.getUser().getUserId(), event.getEventType(), event.getBookingId(), event.getTicketId(),
				event.getOccurredAt().toInstant(ZoneOffset.UTC).toString(),
				event.getMetadata());
	}
}
