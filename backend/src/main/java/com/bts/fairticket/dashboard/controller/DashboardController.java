package com.bts.fairticket.dashboard.controller;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.common.auth.AdminScope;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.dashboard.dto.DashboardSummary;
import com.bts.fairticket.dashboard.service.DashboardQueryService;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
	private final DashboardQueryService service;
	public DashboardController(DashboardQueryService service) { this.service = service; }
	@GetMapping("/summary")
	public ApiResponse<DashboardSummary> summary(
			@RequestParam(required = false) @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String serviceId,
			@RequestAttribute(ApiKeyFilter.ADMIN_ATTRIBUTE) AdminScope scope) {
		return ApiResponse.success(service.summarize(scope.restrict(serviceId)));
	}
}
