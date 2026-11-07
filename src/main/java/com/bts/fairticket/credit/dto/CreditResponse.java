package com.bts.fairticket.credit.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.bts.fairticket.credit.entity.UserCredit;

public record CreditResponse(
    String userId,
    Integer creditScore,
    String tier,
    String updatedAt
) {
    public static CreditResponse from(UserCredit credit) {
        String formattedUpdatedAt = null;
        if (credit.getUpdatedAt() != null) {
            // ISO 8601 UTC/KST 오프셋 포맷팅
            formattedUpdatedAt = credit.getUpdatedAt()
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return new CreditResponse(
            credit.getUserId(),
            credit.getCreditScore(),
            credit.getTier(),
            formattedUpdatedAt
        );
    }
}