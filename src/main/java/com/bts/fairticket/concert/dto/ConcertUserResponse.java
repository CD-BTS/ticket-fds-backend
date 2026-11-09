package com.bts.fairticket.concert.dto;

import java.time.OffsetDateTime;
import com.bts.fairticket.admin.dto.AdminUserResponse;

public record ConcertUserResponse(AdminUserResponse user, OffsetDateTime lastBookingAttemptAt) { }
