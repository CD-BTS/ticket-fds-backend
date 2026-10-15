package com.bts.fairticket.risk.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.risk.dto.UserCalculatedFeatures;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeatureAggregationService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final Clock clock; // 테스트 시 시간 고정을 위해 Clock 주입 (기본 ZoneOffset.UTC)

    @Transactional(readOnly = true)
    public UserCalculatedFeatures aggregateFeatures(String userId, LocalDateTime calculatedAtUtc) {
        LocalDateTime windowStartUtc = calculatedAtUtc.minusDays(30);

        // 1. calculatedAt보다 미래인 이벤트는 제외하고 최근 30일(start <= occurredAt <= end) 조회
        List<BehaviorEvent> events = behaviorEventRepository
                .findByUser_UserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAscEventIdAsc(
                        userId, windowStartUtc, calculatedAtUtc);

        return calculate(events);
    }

    public UserCalculatedFeatures calculate(List<BehaviorEvent> events) {
        // serviceId + bookingId 기준 최초 BOOKING 이벤트 (시간순 보존)
        Map<String, BehaviorEvent> distinctBookings = new LinkedHashMap<>();
        // serviceId + bookingId 기준 취소 키
        Set<String> distinctCancels = new HashSet<>();

        for (BehaviorEvent event : events) {
            if (event.getBookingId() == null || event.getServiceId() == null) {
                continue;
            }

            String bookingKey = event.getServiceId() + ":" + event.getBookingId();

            if ("BOOKING".equalsIgnoreCase(event.getEventType())) {
                // 동일한 serviceId와 bookingId 조합은 최초 1회만 집계
                distinctBookings.putIfAbsent(bookingKey, event);
            } else if ("CANCEL".equalsIgnoreCase(event.getEventType())) {
                distinctCancels.add(bookingKey);
            }
        }

        // 4.1 booking_count
        int bookingCount = distinctBookings.size();

        // 4.2 cancel_count (30일 내 발생한 고유 취소 건수)
        int cancelCount = distinctCancels.size();

        // 4.3 cancel_rate (최근 30일 예매 중 분석 시각까지 취소된 건수 / 최근 30일 전체 예매 건수)
        Double cancelRate = null;
        if (bookingCount > 0) {
            long canceledBookingCount = distinctBookings.keySet().stream()
                    .filter(distinctCancels::contains)
                    .count();
            cancelRate = (double) canceledBookingCount / bookingCount;
        }

        // 4.4 avg_booking_interval & 4.5 short_interval_booking_count
        Double avgBookingInterval = null;
        int shortIntervalBookingCount = 0;

        List<BehaviorEvent> sortedBookings = new ArrayList<>(distinctBookings.values());

        if (sortedBookings.size() >= 2) {
            long totalIntervalSeconds = 0;
            int intervalCount = sortedBookings.size() - 1;

            for (int i = 1; i < sortedBookings.size(); i++) {
                Duration diff = Duration.between(
                        sortedBookings.get(i - 1).getOccurredAt(),
                        sortedBookings.get(i).getOccurredAt()
                );
                long seconds = Math.max(0, diff.getSeconds());
                totalIntervalSeconds += seconds;

                if (seconds <= 5) {
                    shortIntervalBookingCount++;
                }
            }

            avgBookingInterval = (double) totalIntervalSeconds / intervalCount;
        } else {
            // 예매 2건 미만 시 명세 규칙: avg_booking_interval은 null, short_interval은 0
            shortIntervalBookingCount = 0;
        }

        return UserCalculatedFeatures.builder()
                .bookingCount(bookingCount)
                .cancelCount(cancelCount)
                .cancelRate(cancelRate)
                .avgBookingInterval(avgBookingInterval)
                .shortIntervalBookingCount(shortIntervalBookingCount)
                .attendanceRate(null)     // 1차 MVP: null
                .avgBookingDuration(null) // 1차 MVP: null
                .build();
    }
}