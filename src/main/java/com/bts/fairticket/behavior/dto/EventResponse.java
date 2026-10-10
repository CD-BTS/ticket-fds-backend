package com.bts.fairticket.behavior.dto;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.bts.fairticket.behavior.entity.BehaviorEvent;

public record EventResponse(
		@JsonProperty("event_id") Long eventId,
		@JsonProperty("service_id") String serviceId,
		@JsonProperty("external_event_id") String externalEventId,
		@JsonProperty("user_id") String userId,
		@JsonProperty("event_type") String eventType,
		@JsonProperty("booking_id") String bookingId,
		@JsonProperty("ticket_id") String ticketId,
		@JsonProperty("occurred_at") String occurredAt,
		Map<String, Object> metadata) {

	public static EventResponse from(BehaviorEvent event) {
		return new EventResponse(event.getEventId(), event.getServiceId(), event.getExternalEventId(),
				event.getUser().getUserId(), event.getEventType(), event.getBookingId(), event.getTicketId(),
				event.getOccurredAt().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")),
				event.getMetadata());
	}
}
