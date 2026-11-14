package com.bts.fairticket.behavior.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EventType {
	BOOKING, CANCEL, CHECK_IN, BOOKING_ATTEMPT;

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static EventType fromJson(Object value) {
		if (!(value instanceof String name)) {
			throw new IllegalArgumentException("eventType은 BOOKING, CANCEL, CHECK_IN, BOOKING_ATTEMPT 문자열이어야 합니다.");
		}
		return valueOf(name);
	}
}
