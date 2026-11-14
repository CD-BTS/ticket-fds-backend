package com.bts.fairticket.concert.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.concert.dto.*;
import com.bts.fairticket.concert.service.ConcertService;

@RestController
@RequestMapping("/api/v1/concerts")
public class ConcertController {
    private final ConcertService concerts;
    public ConcertController(ConcertService concerts) { this.concerts = concerts; }

    @PutMapping("/{concertId}")
    public ApiResponse<ConcertResponse> upsert(
            @PathVariable @Size(max = 50) @Pattern(regexp = "\\S+") String concertId,
            @Valid @RequestBody ConcertRequest request,
            @RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String serviceId) {
        return ApiResponse.success(concerts.upsert(serviceId, concertId, request));
    }
}
