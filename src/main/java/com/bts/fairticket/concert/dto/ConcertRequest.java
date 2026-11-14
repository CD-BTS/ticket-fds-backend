package com.bts.fairticket.concert.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record ConcertRequest(@NotBlank @Size(max = 200) String name,
        @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
        @NotBlank @Size(max = 255) String venue) {
    @JsonIgnore
    @AssertTrue(message = "공연 시각은 UTC 기준 1000~9999년의 초 단위이며 시작이 종료보다 빨라야 합니다.")
    public boolean isTimeRangeValid() {
        if (startsAt == null || endsAt == null) return true;
        var start = startsAt.withOffsetSameInstant(ZoneOffset.UTC);
        var end = endsAt.withOffsetSameInstant(ZoneOffset.UTC);
        return start.getYear() >= 1000 && end.getYear() <= 9999
                && start.getNano() == 0 && end.getNano() == 0 && start.isBefore(end);
    }
}
