package com.bts.fairticket.concert.dto;

public record ConcertDetailResponse(ConcertResponse concert, long bookingAttemptCount, long bookingAttemptUserCount) { }
