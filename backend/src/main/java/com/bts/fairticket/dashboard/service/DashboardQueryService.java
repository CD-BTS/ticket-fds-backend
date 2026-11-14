package com.bts.fairticket.dashboard.service;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.dashboard.dto.DashboardSummary;
import com.bts.fairticket.dashboard.repository.DashboardQueryRepository;

@Service
public class DashboardQueryService {
	private final DashboardQueryRepository repository;
	public DashboardQueryService(DashboardQueryRepository repository) { this.repository = repository; }

	// All aggregates observe one MySQL repeatable-read snapshot, even when ingestion continues.
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DashboardSummary summarize(Set<String> services) {
		return repository.summarize(services);
	}
}
