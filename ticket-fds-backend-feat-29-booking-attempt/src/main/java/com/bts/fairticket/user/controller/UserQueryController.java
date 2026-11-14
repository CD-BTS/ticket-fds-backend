package com.bts.fairticket.user.controller;

import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.service.UserQueryService;
import com.bts.fairticket.behavior.dto.EventQuery;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.service.UserEventQueryService;
import com.bts.fairticket.common.query.QueryPage;

@RestController
@RequestMapping("/api/v1/users")
public class UserQueryController {
	private final UserQueryService users;
	private final UserEventQueryService events;
	public UserQueryController(UserQueryService users, UserEventQueryService events) { this.users = users; this.events = events; }

	@GetMapping
	public ApiResponse<PageResponse<UserResponse>> list(@Valid @ModelAttribute UserQuery query,
			@RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String service) {
		// Public service lists accept pagination only; administrator filters have a separate contract.
		if (query.serviceId() != null || query.userId() != null || query.externalUserId() != null
				|| query.accountStatus() != null || query.tier() != null || query.minCreditScore() != null
				|| query.maxCreditScore() != null || query.createdFrom() != null || query.createdTo() != null) QueryPage.invalid();
		return ApiResponse.success(users.find(query, Set.of(service)));
	}

	@GetMapping("/{userId}/events")
	public ApiResponse<PageResponse<EventResponse>> events(@PathVariable @NotBlank @Size(max = 50) String userId,
			@Valid @ModelAttribute EventQuery query,
			@RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String service) {
		return ApiResponse.success(events.find(userId, service, query));
	}
}
