package com.bts.fairticket.policy.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.risk.entity.RiskAssessment;

@Entity
@Table(name = "policy_decision",
		indexes = {
				@Index(name = "policy_decision_index_13", columnList = "user_id,decided_at"),
				@Index(name = "policy_decision_index_14", columnList = "user_id,action,decided_at"),
				@Index(name = "policy_decision_index_15", columnList = "user_id,context,decided_at"),
				@Index(name = "policy_decision_index_16", columnList = "assessment_id"),
				@Index(name = "policy_decision_index_17", columnList = "decided_at,action")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "decision_id", nullable = false)
	private Long decisionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "assessment_id", referencedColumnName = "assessment_id", nullable = true)
	private RiskAssessment assessment;

	@Column(name = "context", nullable = false, length = 30)
	private String context;

	@Column(name = "credit_score_at_decision", nullable = false)
	private Integer creditScoreAtDecision;

	@Column(name = "anomaly_score_at_decision", nullable = true, columnDefinition = "double")
	private Double anomalyScoreAtDecision;

	@Column(name = "action", nullable = false, length = 30)
	private String action;

	@Column(name = "reason", nullable = true, length = 255)
	private String reason;

	@Generated(event = EventType.INSERT)
	@ColumnDefault("(now())")
	@Column(name = "decided_at", nullable = false, columnDefinition = "datetime", insertable = false, updatable = false)
	private LocalDateTime decidedAt;

	public PolicyDecision(UserAccount user, RiskAssessment assessment, String context, Integer creditScoreAtDecision,
			Double anomalyScoreAtDecision, String action, String reason) {
		this.user = user;
		this.assessment = assessment;
		this.context = context;
		this.creditScoreAtDecision = creditScoreAtDecision;
		this.anomalyScoreAtDecision = anomalyScoreAtDecision;
		this.action = action;
		this.reason = reason;
	}
}
