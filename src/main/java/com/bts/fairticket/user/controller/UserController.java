package com.bts.fairticket.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<UserResponse>> register(
			@Valid @RequestBody UserRegistrationRequest request,
			@RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String authenticatedService) {
		var result = userService.register(request, authenticatedService);
		ResponseCode code = result.created() ? ResponseCode.CREATED : ResponseCode.SUCCESS;
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.success(code, result.user()));
	}

	@GetMapping("/{userId}")
	public ApiResponse<UserResponse> getUser(
			@PathVariable @NotBlank @Size(max = 50) String userId,
			@RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String authenticatedService) {
		return ApiResponse.success(userService.getUser(userId, authenticatedService));
	}
}
