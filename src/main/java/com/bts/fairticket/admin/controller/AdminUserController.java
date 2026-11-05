package com.bts.fairticket.admin.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.common.auth.AdminScope;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.service.UserQueryService;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
	private final UserQueryService users;
	public AdminUserController(UserQueryService users) { this.users = users; }

	@GetMapping
	public ApiResponse<PageResponse<UserResponse>> list(@Valid @ModelAttribute UserQuery query,
			@RequestAttribute(ApiKeyFilter.ADMIN_ATTRIBUTE) AdminScope scope) {
		return ApiResponse.success(users.find(query, scope.restrict(query.serviceId())));
	}
}
