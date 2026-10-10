package com.bts.fairticket.behavior.entity;

import java.time.LocalDateTime;
import java.util.Map;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.bts.fairticket.user.entity.UserAccount;

@Entity
@Table(name = "behavior_event",
		uniqueConstraints = @UniqueConstraint(name = "behavior_event_index_3",
				columnNames = {"service_id", "external_event_id"}),
		indexes = {
				@Index(name = "behavior_event_index_4", columnList = "user_id"),
				@Index(name = "behavior_event_index_5", columnList = "user_id,occurred_at"),
				@Index(name = "behavior_event_index_6", columnList = "user_id,event_type,occurred_at"),
				@Index(name = "behavior_event_index_7", columnList = "service_id,booking_id")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviorEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_id", nullable = false)
	private Long eventId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "service_id", nullable = false, length = 50)
	private String serviceId;

	@Column(name = "external_event_id", nullable = false, length = 100)
	private String externalEventId;

	@Column(name = "event_type", nullable = false, length = 30)
	private String eventType;

	@Column(name = "booking_id", nullable = true, length = 100)
	private String bookingId;

	@Column(name = "ticket_id", nullable = true, length = 100)
	private String ticketId;

	@Column(name = "occurred_at", nullable = false, columnDefinition = "datetime")
	private LocalDateTime occurredAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = true, columnDefinition = "json")
	private Map<String, Object> metadata;

	public BehaviorEvent(UserAccount user, String serviceId, String externalEventId, String eventType,
			String bookingId, String ticketId, LocalDateTime occurredAt, Map<String, Object> metadata) {
		this.user = user;
		this.serviceId = serviceId;
		this.externalEventId = externalEventId;
		this.eventType = eventType;
		this.bookingId = bookingId;
		this.ticketId = ticketId;
		this.occurredAt = occurredAt;
		this.metadata = metadata;
	}
}
