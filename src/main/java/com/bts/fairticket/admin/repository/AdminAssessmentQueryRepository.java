package com.bts.fairticket.admin.repository;

import java.util.Map;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import com.bts.fairticket.admin.dto.AssessmentQuery;
import com.bts.fairticket.admin.dto.AssessmentResponse;
import com.bts.fairticket.common.query.QueryPage;
import com.bts.fairticket.common.response.PageResponse;

@Repository
public class AdminAssessmentQueryRepository {
    private final EntityManager em;
    public AdminAssessmentQueryRepository(EntityManager em) { this.em = em; }

    public PageResponse<AssessmentResponse> find(String userId, AssessmentQuery filter) {
        var page = QueryPage.parse(filter.page(), filter.size(), filter.sort(), Map.of(
                "assessedAt", "a.assessedAt", "assessmentId", "a.assessmentId",
                "anomalyScore", "a.anomalyScore"), "assessedAt", "a.assessmentId");
        long total = em.createQuery("select count(a) from RiskAssessment a where a.user.userId = :userId", Long.class)
                .setParameter("userId", userId).getSingleResult();
        // A policy can be absent or reused multiple times. Keep only its latest decision per assessment.
        var content = em.createQuery("""
                select new com.bts.fairticket.admin.dto.AssessmentResponse(
                    a.assessmentId, a.anomalyScore, p.action, a.modelVersion, a.assessedAt)
                from RiskAssessment a left join PolicyDecision p
                    on p.assessment.assessmentId = a.assessmentId and p.user.userId = a.user.userId
                where a.user.userId = :userId and (p.decisionId is null or not exists (
                    select newer.decisionId from PolicyDecision newer
                    where newer.assessment.assessmentId = a.assessmentId and newer.user.userId = a.user.userId
                    and (newer.decidedAt > p.decidedAt
                        or (newer.decidedAt = p.decidedAt and newer.decisionId > p.decisionId))))
                order by
                """ + page.orderBy(), AssessmentResponse.class)
                .setParameter("userId", userId).setFirstResult(page.offset()).setMaxResults(page.size()).getResultList();
        return PageResponse.of(content, page.page(), page.size(), total);
    }
}
