package com.bts.fairticket.risk.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.risk.dto.UserCalculatedFeatures;
import com.bts.fairticket.user.entity.UserAccount;

@ExtendWith(MockitoExtension.class)
class FeatureAggregationServiceTest {

    @Mock
    private BehaviorEventRepository behaviorEventRepository;

    private FeatureAggregationService service;
    private UserAccount mockUser;

    @BeforeEach
    void setUp() {
        service = new FeatureAggregationService(behaviorEventRepository, Clock.systemUTC());
        mockUser = new UserAccount("user_001", "service_A", "ext_001");
    }

    @Test
    @DisplayName("이벤트가 전혀 없는 경우: booking=0, cancel=0, cancelRate=null, interval=null, shortInterval=0")
    void calculate_emptyEvents() {
        UserCalculatedFeatures result = service.calculate(Collections.emptyList());

        assertEquals(0, result.getBookingCount());
        assertEquals(0, result.getCancelCount());
        assertNull(result.getCancelRate());
        assertNull(result.getAvgBookingInterval());
        assertEquals(0, result.getShortIntervalBookingCount());
    }

    @Test
    @DisplayName("예매가 1건만 있는 경우: 간격 계산 불가로 interval은 null, shortInterval은 0 반환")
    void calculate_singleBooking() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 0, 0);
        BehaviorEvent event = new BehaviorEvent(mockUser, "service_A", "evt_1", "BOOKING", "book_1", null, now, null);

        UserCalculatedFeatures result = service.calculate(List.of(event));

        assertEquals(1, result.getBookingCount());
        assertEquals(0, result.getCancelCount());
        assertEquals(0.0, result.getCancelRate());
        assertNull(result.getAvgBookingInterval());
        assertEquals(0, result.getShortIntervalBookingCount());
    }

    @Test
    @DisplayName("동일한 serviceId + bookingId 중복 이벤트는 1건으로만 집계")
    void calculate_duplicateBookings() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 15, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 15, 10, 0, 2);

        BehaviorEvent e1 = new BehaviorEvent(mockUser, "service_A", "evt_1", "BOOKING", "book_1", null, t1, null);
        BehaviorEvent e2 = new BehaviorEvent(mockUser, "service_A", "evt_2", "BOOKING", "book_1", null, t2, null);

        UserCalculatedFeatures result = service.calculate(List.of(e1, e2));

        assertEquals(1, result.getBookingCount());
        assertNull(result.getAvgBookingInterval());
    }

    @Test
    @DisplayName("간격 계산 및 5초 이하 짧은 간격 카운팅 정상 동작 확인")
    void calculate_intervalsAndShortIntervalCount() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 15, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 15, 10, 0, 4);  // 간격 4초 (<= 5초)
        LocalDateTime t3 = LocalDateTime.of(2026, 10, 15, 10, 0, 10); // 간격 6초 (> 5초)

        BehaviorEvent b1 = new BehaviorEvent(mockUser, "service_A", "e1", "BOOKING", "b1", null, t1, null);
        BehaviorEvent b2 = new BehaviorEvent(mockUser, "service_A", "e2", "BOOKING", "b2", null, t2, null);
        BehaviorEvent b3 = new BehaviorEvent(mockUser, "service_A", "e3", "BOOKING", "b3", null, t3, null);

        // b1이 취소됨
        BehaviorEvent c1 = new BehaviorEvent(mockUser, "service_A", "e4", "CANCEL", "b1", null, t3, null);

        UserCalculatedFeatures result = service.calculate(List.of(b1, b2, b3, c1));

        assertEquals(3, result.getBookingCount());
        assertEquals(1, result.getCancelCount());
        assertEquals(1.0 / 3.0, result.getCancelRate(), 0.0001);
        assertEquals(5.0, result.getAvgBookingInterval(), 0.0001); // (4 + 6) / 2 = 5.0초
        assertEquals(1, result.getShortIntervalBookingCount());    // 4초 간격 1회
    }
}