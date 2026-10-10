package com.bts.fairticket.behavior.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventRequest(
		@JsonProperty("service_id") @NotBlank @Size(max = 50) String serviceId,
		@JsonProperty("external_event_id") @NotBlank @Size(max = 100) String externalEventId,
		@JsonProperty("user_id") @NotBlank @Size(max = 50) String userId,
		@JsonProperty("event_type") @NotNull EventType eventType,
		@JsonProperty("booking_id") @Size(max = 100) String bookingId,
		@JsonProperty("ticket_id") @Size(max = 100) String ticketId,
		@JsonProperty("occurred_at") @NotBlank
		@Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}") String occurredAt,
		Map<String, Object> metadata) {

	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

	@JsonIgnore
	@AssertTrue(message = "BOOKING/CANCEL은 booking_id, CHECK_IN은 ticket_id가 필요합니다.")
	public boolean isRequiredIdentifierValid() {
		if (eventType == null) {
			return true;
		}
		String identifier = eventType == EventType.CHECK_IN ? ticketId : bookingId;
		return identifier != null && !identifier.isBlank();
	}

	@JsonIgnore
	@AssertTrue(message = "occurred_at은 유효한 서울 기준 yyyy-MM-dd'T'HH:mm:ss 시각이어야 합니다.")
	public boolean isOccurrenceTimeValid() {
		if (occurredAt == null) {
			return true;
		}
		try {
			return occurrenceTime().getYear() >= 1000;
		} catch (DateTimeParseException exception) {
			return false;
		}
	}

	public LocalDateTime occurrenceTime() {
		return LocalDateTime.parse(occurredAt, TIME_FORMAT);
	}
}
