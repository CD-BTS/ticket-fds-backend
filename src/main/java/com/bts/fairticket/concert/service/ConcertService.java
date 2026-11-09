package com.bts.fairticket.concert.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.admin.service.AdminUserQueryService;
import com.bts.fairticket.common.auth.AdminScope;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.concert.dto.*;
import com.bts.fairticket.concert.repository.ConcertRepository;
import com.bts.fairticket.user.dto.UserQuery;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ConcertService {
    private final ConcertRepository repository;
    private final AdminUserQueryService users;
    public ConcertService(ConcertRepository repository, AdminUserQueryService users) {
        this.repository = repository; this.users = users;
    }

    @Transactional
    public ConcertResponse upsert(String serviceId, String concertId, ConcertRequest request) {
        repository.upsert(serviceId, concertId, request);
        return require(serviceId, concertId);
    }

    public PageResponse<ConcertResponse> list(String serviceId, ConcertQuery query, AdminScope scope) {
        scope.restrict(serviceId);
        return repository.list(serviceId, query);
    }

    public ConcertDetailResponse detail(String serviceId, String concertId, AdminScope scope) {
        scope.restrict(serviceId);
        var concert = require(serviceId, concertId);
        var counts = repository.statistics(serviceId, concertId);
        return new ConcertDetailResponse(concert, counts[0], counts[1]);
    }

    public PageResponse<ConcertUserResponse> users(String serviceId, String concertId, UserQuery query, AdminScope scope) {
        scope.restrict(serviceId);
        require(serviceId, concertId);
        var page = users.findForConcert(query, scope, concertId);
        var activity = repository.lastActivities(serviceId, concertId,
                page.content().stream().map(user -> user.userId()).toList());
        return PageResponse.of(page.content().stream()
                .map(user -> new ConcertUserResponse(user, activity.get(user.userId()))).toList(),
                page.page(), page.size(), page.totalElements());
    }

    private ConcertResponse require(String serviceId, String concertId) {
        return repository.find(serviceId, concertId)
                .orElseThrow(() -> new ApiException(ResponseCode.CONCERT_NOT_FOUND));
    }
}
