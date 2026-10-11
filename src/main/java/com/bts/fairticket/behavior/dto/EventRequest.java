package com.bts.fairticket.behavior.dto;

import java.math.BigDecimal;
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
		@JsonProperty("serviceId") @NotBlank @Size(max = 50) String serviceId,
		@JsonProperty("externalEventId") @NotBlank @Size(max = 100) String externalEventId,
		@JsonProperty("userId") @NotBlank @Size(max = 50) String userId,
		@JsonProperty("eventType") @NotNull EventType eventType,
		@JsonProperty("bookingId") @Size(max = 100) String bookingId,
		@JsonProperty("ticketId") @Size(max = 100) String ticketId,
		@JsonProperty("occurredAt") @NotBlank
		@Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}") String occurredAt,
		Map<String, Object> metadata) {

	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

	@JsonIgnore
	@AssertTrue(message = "BOOKING/CANCEL은 bookingId, CHECK_IN은 ticketId가 필요합니다.")
	public boolean isRequiredIdentifierValid() {
		if (eventType == null) {
			return true;
		}
		String identifier = eventType == EventType.CHECK_IN ? ticketId : bookingId;
		return identifier != null && !identifier.isBlank();
	}

	@JsonIgnore
	@AssertTrue(message = "occurredAt은 유효한 서울 기준 yyyy-MM-dd'T'HH:mm:ss 시각이어야 합니다.")
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

	@JsonIgnore
	@AssertTrue(message = "bookingDurationSeconds는 0 이상의 숫자, deviceChanged는 boolean이어야 합니다.")
	public boolean isMetadataValid() {
		if (metadata == null) {
			return true;
		}
		if (metadata.containsKey("bookingDurationSeconds")) {
			Object value = metadata.get("bookingDurationSeconds");
			if (!(value instanceof Number number)) {
				return false;
			}
			try {
				if (new BigDecimal(number.toString()).signum() < 0) {
					return false;
				}
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return !metadata.containsKey("deviceChanged") || metadata.get("deviceChanged") instanceof Boolean;
	}
}
