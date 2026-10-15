package com.bts.fairticket.behavior.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.behavior.entity.BehaviorEvent;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {
	Optional<BehaviorEvent> findByServiceIdAndExternalEventId(String serviceId, String externalEventId);

	// 최근 30일 이벤트 조회 (occurredAt 오름차순, 동일 시각이면 eventId 오름차순)
	List<BehaviorEvent> findByUser_UserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAscEventIdAsc(
			String userId, LocalDateTime start, LocalDateTime end);
}