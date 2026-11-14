package com.bts.fairticket.admin.dto;

import java.time.LocalDateTime;

/** policyAction is the latest persisted policy action linked to this assessment, or null; not an AI classification. */
public record AssessmentResponse(Long assessmentId, Double anomalyScore, String policyAction,
        String modelVersion, LocalDateTime assessedAt) { }
