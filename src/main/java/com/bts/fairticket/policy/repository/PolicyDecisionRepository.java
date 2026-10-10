package com.bts.fairticket.policy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.policy.entity.PolicyDecision;

public interface PolicyDecisionRepository extends JpaRepository<PolicyDecision, Long> {
}
