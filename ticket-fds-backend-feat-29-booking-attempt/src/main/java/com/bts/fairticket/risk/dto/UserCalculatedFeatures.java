package com.bts.fairticket.risk.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserCalculatedFeatures {
    private Integer bookingCount;              // 최근 30일 고유 예매 건수 (기본 0)
    private Integer cancelCount;               // 최근 30일 고유 취소 건수 (기본 0)
    private Double cancelRate;                 // 예매 대비 취소 비율 (예매 0건 시 null)
    private Double avgBookingInterval;         // 평균 예매 간격(초) (예매 2건 미만 시 null)
    private Integer shortIntervalBookingCount; // 5초 이하 연속 예매 횟수 (예매 2건 미만 시 0)
    private Double attendanceRate;             // 1차 MVP: null
    private Double avgBookingDuration;         // 1차 MVP: null
}