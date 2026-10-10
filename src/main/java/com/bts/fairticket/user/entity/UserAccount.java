package com.bts.fairticket.user.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "user_account",
		uniqueConstraints = @UniqueConstraint(name = "user_account_index_0",
				columnNames = {"service_id", "external_user_id"}),
		indexes = {
				@Index(name = "user_account_index_1", columnList = "service_id"),
				@Index(name = "user_account_index_2", columnList = "account_status")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

	@Id
	@Column(name = "user_id", nullable = false, length = 50)
	private String userId;

	@Column(name = "service_id", nullable = false, length = 50)
	private String serviceId;

	@Column(name = "external_user_id", nullable = false, length = 100)
	private String externalUserId;

	@Generated(event = EventType.INSERT)
	@ColumnDefault("(now())")
	@Column(name = "created_at", nullable = false, columnDefinition = "datetime", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@ColumnDefault("'ACTIVE'")
	@Column(name = "account_status", nullable = false, length = 20)
	private String accountStatus = "ACTIVE";

	public UserAccount(String userId, String serviceId, String externalUserId) {
		this.userId = userId;
		this.serviceId = serviceId;
		this.externalUserId = externalUserId;
	}
}
