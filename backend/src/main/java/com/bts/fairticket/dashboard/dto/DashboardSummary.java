package com.bts.fairticket.dashboard.dto;

import java.util.Map;

public record DashboardSummary(long totalUsers, long activeUsers, long suspendedUsers,
		long usersWithCredit, Double averageCreditScore, Map<String, Long> usersByTier,
		long totalEvents, Map<String, Long> eventsByType) { }
