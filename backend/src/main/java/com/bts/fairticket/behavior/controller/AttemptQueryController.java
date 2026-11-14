package com.bts.fairticket.behavior.controller;

import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.bts.fairticket.behavior.service.AttemptQueryService;
import com.bts.fairticket.common.auth.ApiKeyFilter;
import com.bts.fairticket.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/events/booking-attempts")
public class AttemptQueryController {
    private final AttemptQueryService service;
    public AttemptQueryController(AttemptQueryService service) { this.service=service; }
    public record Result(long bookingAttemptCount,List<Map<String,Object>> items){}
    @GetMapping
    public ApiResponse<Result> find(
            @RequestAttribute(ApiKeyFilter.SERVICE_ATTRIBUTE) String scope,
            @RequestParam(required=false) @Size(max=50) String concertId,
            @RequestParam(required=false) @Size(max=50) String userId,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(new Result(service.count(scope,concertId,userId),service.find(scope,concertId,userId,page*size,size)));
    }
}
