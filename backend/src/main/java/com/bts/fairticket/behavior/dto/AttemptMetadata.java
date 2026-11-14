package com.bts.fairticket.behavior.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Raw attempt events, not AI features. Existing event metadata remains unchanged. */
public final class AttemptMetadata {
    private AttemptMetadata() {}
    public static final Set<String> STAGES = Set.of("STARTED", "CAPTCHA", "QUEUE", "SEAT_SELECTION", "SEAT_HOLD", "PAYMENT", "COMPLETED");
    public static final Set<String> STATUSES = Set.of("ENTERED", "SUCCEEDED", "REJECTED", "FAILED", "ABANDONED", "EXPIRED");
    public static boolean valid(Map<String, Object> m) {
        if (m == null) return false;
        try {
            if (!(m.get("attemptId") instanceof String id) || !UUID.fromString(id).toString().equals(id)) return false;
            if (!(m.get("transitionId") instanceof String transition) || transition.isBlank() || transition.length() > 100) return false;
            if (!(m.get("concertId") instanceof String concert) || concert.isBlank() || concert.length() > 50) return false;
            if (!STAGES.contains(m.get("stage")) || !STATUSES.contains(m.get("status"))) return false;
            if (!(m.get("sequence") instanceof Number n) || new BigDecimal(n.toString()).longValueExact() < 1) return false;
            if (!Set.of("SERVER", "CLIENT_REPORT").contains(m.get("source"))) return false;
            if (!(m.get("recordedAt") instanceof String at)) return false;
            OffsetDateTime.parse(at);
            if ("STARTED".equals(m.get("stage")) && "ENTERED".equals(m.get("status")) && new BigDecimal(n.toString()).longValueExact() != 1) return false;
            if ("STARTED".equals(m.get("stage")) && !Set.of("ENTERED","ABANDONED").contains(m.get("status"))) return false;
            if ("COMPLETED".equals(m.get("stage")) && (!"SUCCEEDED".equals(m.get("status")) || !(m.get("bookingId") instanceof String booking) || booking.isBlank())) return false;
            return true;
        } catch (RuntimeException e) { return false; }
    }
}
