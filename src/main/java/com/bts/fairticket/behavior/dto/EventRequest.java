package com.bts.fairticket.behavior.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRequest(
		@JsonProperty("serviceId") @NotBlank @Size(max = 50) String serviceId,
		@JsonProperty("externalEventId") @NotBlank @Size(max = 100) String externalEventId,
		@JsonProperty("userId") @NotBlank @Size(max = 50) String userId,
		@JsonProperty("eventType") @NotNull EventType eventType,
		@JsonProperty("bookingId") @Size(max = 100) String bookingId,
		@JsonProperty("ticketId") @Size(max = 100) String ticketId,
		@JsonProperty("occurredAt") @NotBlank String occurredAt,
		Map<String, Object> metadata) {

	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ISO_OFFSET_DATE_TIME;

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
	@AssertTrue(message = "occurredAt은 UTC 오프셋을 포함하고 UTC 기준 1000~9999년 범위의 초 단위 ISO 8601 시각이어야 합니다.")
	public boolean isOccurrenceTimeValid() {
		if (occurredAt == null) {
			return true;
		}
		try {
			LocalDateTime utc = occurrenceTime();
			return utc.getYear() >= 1000 && utc.getYear() <= 9999 && utc.getNano() == 0;
		} catch (DateTimeParseException exception) {
			return false;
		}
	}

	public LocalDateTime occurrenceTime() {
		// DATETIME에는 시간대가 없으므로 이벤트 API의 저장 경계에서 UTC로 변환한다.
		return OffsetDateTime.parse(occurredAt, TIME_FORMAT)
				.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
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
