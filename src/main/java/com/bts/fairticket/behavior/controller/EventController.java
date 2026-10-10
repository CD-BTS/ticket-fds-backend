package com.bts.fairticket.behavior.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.service.EventService;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
	private final EventService service;

	public EventController(EventService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<EventResponse>> collect(@Valid @RequestBody EventRequest request,
			@RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String authenticatedService) {
		var result = service.collect(request, authenticatedService);
		var code = result.created() ? ResponseCode.CREATED : ResponseCode.SUCCESS;
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.success(code, result.event()));
	}
}
