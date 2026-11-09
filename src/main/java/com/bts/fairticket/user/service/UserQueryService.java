package com.bts.fairticket.user.service;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.user.repository.UserQueryRepository;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.common.response.PageResponse;

@Service
@Transactional(readOnly = true)
public class UserQueryService {
	private final UserQueryRepository repository;
	public UserQueryService(UserQueryRepository repository) { this.repository = repository; }
	public PageResponse<UserResponse> find(UserQuery query, Set<String> services) {
		return repository.find(query, services);
	}
	public PageResponse<UserResponse> findForConcert(UserQuery query, Set<String> services, String concertId) {
		return repository.find(query, services, concertId);
	}
}
