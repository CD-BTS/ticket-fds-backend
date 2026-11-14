package com.bts.fairticket.behavior.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.behavior.dto.EventQuery;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.repository.UserEventQueryRepository;
import com.bts.fairticket.common.response.PageResponse;

@Service
public class UserEventQueryService {
	private final UserEventQueryRepository repository;
	public UserEventQueryService(UserEventQueryRepository repository) { this.repository = repository; }

	@Transactional(readOnly = true)
	public PageResponse<EventResponse> find(String userId, String serviceId, EventQuery query) {
		return repository.find(userId, serviceId, query);
	}
}
