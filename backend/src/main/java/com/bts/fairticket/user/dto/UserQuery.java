package com.bts.fairticket.user.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

public record UserQuery(Integer page, Integer size, String sort,
		@Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String serviceId,
		@Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String userId,
		@Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String externalUserId,
		@Pattern(regexp = "ACTIVE|SUSPENDED") String accountStatus,
		@Size(min = 1, max = 20) @Pattern(regexp = ".*\\S.*") String tier,
		@Min(0) @Max(100) Integer minCreditScore, @Min(0) @Max(100) Integer maxCreditScore,
		@DateTimeFormat(iso = DATE_TIME) LocalDateTime createdFrom,
		@DateTimeFormat(iso = DATE_TIME) LocalDateTime createdTo) { }
