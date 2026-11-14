package com.bts.fairticket.risk.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.risk.entity.RiskAssessment;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
	@Query("""
		select a from RiskAssessment a join fetch a.user
		where a.user.userId in :userIds and not exists (
		    select newer.assessmentId from RiskAssessment newer where newer.user.userId = a.user.userId
		    and (newer.assessedAt > a.assessedAt
		        or (newer.assessedAt = a.assessedAt and newer.assessmentId > a.assessmentId)))
		""")
	List<RiskAssessment> findLatestForUsers(@Param("userIds") List<String> userIds);
	Optional<RiskAssessment> findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(String userId);
}
