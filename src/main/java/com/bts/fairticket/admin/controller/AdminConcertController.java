package com.bts.fairticket.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.common.auth.AdminScope;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.*;
import com.bts.fairticket.concert.dto.*;
import com.bts.fairticket.concert.service.ConcertService;
import com.bts.fairticket.user.dto.UserQuery;

@RestController
@RequestMapping("/api/v1/admin/concerts")
public class AdminConcertController {
    private final ConcertService concerts;
    public AdminConcertController(ConcertService concerts) { this.concerts = concerts; }

    @GetMapping
    public ApiResponse<PageResponse<ConcertResponse>> list(
            @RequestParam @Size(max = 50) @Pattern(regexp = "\\S+") String serviceId,
            @ModelAttribute ConcertQuery query, @RequestAttribute(ApiKeyFilter.ADMIN_ATTRIBUTE) AdminScope scope) {
        return ApiResponse.success(concerts.list(serviceId, query, scope));
    }

    @GetMapping("/{concertId}")
    public ApiResponse<ConcertDetailResponse> detail(
            @PathVariable @Size(max = 50) @Pattern(regexp = "\\S+") String concertId,
            @RequestParam @Size(max = 50) @Pattern(regexp = "\\S+") String serviceId,
            @RequestAttribute(ApiKeyFilter.ADMIN_ATTRIBUTE) AdminScope scope) {
        return ApiResponse.success(concerts.detail(serviceId, concertId, scope));
    }

    @GetMapping("/{concertId}/users")
    public ApiResponse<PageResponse<ConcertUserResponse>> users(
            @PathVariable @Size(max = 50) @Pattern(regexp = "\\S+") String concertId,
            @RequestParam @Size(max = 50) @Pattern(regexp = "\\S+") String serviceId,
            @Valid @ModelAttribute UserQuery query, @RequestAttribute(ApiKeyFilter.ADMIN_ATTRIBUTE) AdminScope scope) {
        return ApiResponse.success(concerts.users(serviceId, concertId, query, scope));
    }
}
