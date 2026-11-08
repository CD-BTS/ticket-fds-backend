package com.bts.fairticket.behavior.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

public record EventQuery(Integer page, Integer size, String sort,
		@Pattern(regexp = "BOOKING|CANCEL|CHECK_IN|BOOKING_ATTEMPT") String eventType,
		@DateTimeFormat(iso = DATE_TIME) OffsetDateTime from,
		@DateTimeFormat(iso = DATE_TIME) OffsetDateTime to) { }
