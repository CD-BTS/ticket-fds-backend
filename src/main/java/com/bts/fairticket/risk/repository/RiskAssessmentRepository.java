package com.bts.fairticket.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.risk.entity.RiskAssessment;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
}
