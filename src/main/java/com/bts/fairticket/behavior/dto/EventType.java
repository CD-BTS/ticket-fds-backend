package com.bts.fairticket.behavior.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EventType {
	BOOKING, CANCEL, CHECK_IN;

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static EventType fromJson(Object value) {
		if (!(value instanceof String name)) {
			throw new IllegalArgumentException("event_type은 BOOKING, CANCEL, CHECK_IN 문자열이어야 합니다.");
		}
		return valueOf(name);
	}
}
