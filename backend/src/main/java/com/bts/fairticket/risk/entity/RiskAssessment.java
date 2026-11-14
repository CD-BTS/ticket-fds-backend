package com.bts.fairticket.risk.entity;

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

@Entity
@Table(name = "risk_assessment",
		indexes = {
				@Index(name = "risk_assessment_index_11", columnList = "user_id,assessed_at"),
				@Index(name = "risk_assessment_index_12", columnList = "assessed_at")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "assessment_id", nullable = false)
	private Long assessmentId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "anomaly_score", nullable = false, columnDefinition = "double")
	private Double anomalyScore;

	@Column(name = "model_version", nullable = false, length = 30)
	private String modelVersion;

	@Generated(event = EventType.INSERT)
	@ColumnDefault("(now())")
	@Column(name = "assessed_at", nullable = false, columnDefinition = "datetime", insertable = false, updatable = false)
	private LocalDateTime assessedAt;

	public RiskAssessment(UserAccount user, Double anomalyScore, String modelVersion) {
		this.user = user;
		this.anomalyScore = anomalyScore;
		this.modelVersion = modelVersion;
	}
}
