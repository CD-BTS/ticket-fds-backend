package com.bts.fairticket.concert.dto;

import java.time.OffsetDateTime;

public record ConcertResponse(String serviceId, String concertId, String name,
        OffsetDateTime startsAt, OffsetDateTime endsAt, String venue) { }
