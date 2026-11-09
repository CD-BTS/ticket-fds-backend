package com.bts.fairticket.admin.dto;

import com.bts.fairticket.user.dto.UserDetailResponse;
import com.bts.fairticket.policy.dto.PolicyDecisionResponse;

public record AdminUserDetailResponse(UserDetailResponse user, PolicyDecisionResponse latestPolicy) { }
