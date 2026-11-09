package com.bts.fairticket.admin.service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bts.fairticket.admin.dto.*;
import com.bts.fairticket.admin.repository.AdminAssessmentQueryRepository;
import com.bts.fairticket.behavior.dto.EventQuery;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.service.UserEventQueryService;
import com.bts.fairticket.common.auth.AdminScope;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;
import com.bts.fairticket.user.service.UserQueryService;
import com.bts.fairticket.user.service.UserService;

@Service
@Transactional(readOnly = true)
public class AdminUserQueryService {
    private final UserQueryService users;
    private final UserService details;
    private final UserEventQueryService events;
    private final UserAccountRepository accounts;
    private final RiskAssessmentRepository assessments;
    private final PolicyDecisionRepository policies;
    private final AdminAssessmentQueryRepository history;

    public AdminUserQueryService(UserQueryService users, UserService details, UserEventQueryService events,
            UserAccountRepository accounts, RiskAssessmentRepository assessments,
            PolicyDecisionRepository policies, AdminAssessmentQueryRepository history) {
        this.users = users; this.details = details; this.events = events; this.accounts = accounts;
        this.assessments = assessments; this.policies = policies; this.history = history;
    }

    public PageResponse<AdminUserResponse> find(UserQuery query, AdminScope scope) {
        return enrich(users.find(query, scope.restrict(query.serviceId())));
    }

    public PageResponse<AdminUserResponse> findForConcert(UserQuery query, AdminScope scope, String concertId) {
        return enrich(users.findForConcert(query, scope.restrict(query.serviceId()), concertId));
    }

    private PageResponse<AdminUserResponse> enrich(PageResponse<com.bts.fairticket.user.dto.UserResponse> page) {
        Map<String, RiskAssessment> latest = page.content().isEmpty() ? Map.of() : assessments
                .findLatestForUsers(page.content().stream().map(user -> user.userId()).toList()).stream()
                .collect(Collectors.toMap(a -> a.getUser().getUserId(), Function.identity()));
        return PageResponse.of(page.content().stream()
                .map(user -> AdminUserResponse.from(user, latest.get(user.userId()))).toList(),
                page.page(), page.size(), page.totalElements());
    }

    public AdminUserDetailResponse detail(String userId, AdminScope scope) {
        var user = authorizedUser(userId, scope);
        var detail = details.getUser(user.getUserId(), user.getServiceId());
        var policy = policies.findFirstByUser_UserIdOrderByDecidedAtDescDecisionIdDesc(user.getUserId())
                .map(PolicyDecisionResponse::from).orElse(null);
        return new AdminUserDetailResponse(detail, policy);
    }

    public PageResponse<EventResponse> events(String userId, EventQuery query, AdminScope scope) {
        var user = authorizedUser(userId, scope);
        return events.find(user.getUserId(), user.getServiceId(), query);
    }

    public PageResponse<AssessmentResponse> assessments(String userId, AssessmentQuery query, AdminScope scope) {
        return history.find(authorizedUser(userId, scope).getUserId(), query);
    }

    private UserAccount authorizedUser(String userId, AdminScope scope) {
        var user = accounts.findById(userId)
                .orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));
        // Hide out-of-scope users with the same response as missing users.
        if (!scope.allServices() && !scope.services().contains(user.getServiceId())) {
            throw new ApiException(ResponseCode.USER_NOT_FOUND);
        }
        return user;
    }
}
