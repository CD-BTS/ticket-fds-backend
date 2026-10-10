package com.bts.fairticket.credit.entity;

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
import com.bts.fairticket.behavior.entity.BehaviorEvent;

@Entity
@Table(name = "credit_history",
		indexes = {
				@Index(name = "credit_history_index_9", columnList = "user_id,created_at"),
				@Index(name = "credit_history_index_10", columnList = "event_id")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "history_id", nullable = false)
	private Long historyId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "event_id", referencedColumnName = "event_id", nullable = true)
	private BehaviorEvent event;

	@Column(name = "score_before", nullable = false)
	private Integer scoreBefore;

	@Column(name = "score_after", nullable = false)
	private Integer scoreAfter;

	@Column(name = "reason", nullable = false, length = 255)
	private String reason;

	@Generated(event = EventType.INSERT)
	@ColumnDefault("(now())")
	@Column(name = "created_at", nullable = false, columnDefinition = "datetime", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public CreditHistory(UserAccount user, BehaviorEvent event, Integer scoreBefore, Integer scoreAfter, String reason) {
		this.user = user;
		this.event = event;
		this.scoreBefore = scoreBefore;
		this.scoreAfter = scoreAfter;
		this.reason = reason;
	}
}
