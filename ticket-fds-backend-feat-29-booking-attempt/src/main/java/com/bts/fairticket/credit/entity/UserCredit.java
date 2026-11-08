package com.bts.fairticket.credit.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import com.bts.fairticket.user.entity.UserAccount;

@Entity
@Table(name = "user_credit",
		indexes = {
				@Index(name = "user_credit_index_8", columnList = "tier")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredit {

	@Id
	@Column(name = "user_id", nullable = false, length = 50)
	private String userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
	private UserAccount user;

	@ColumnDefault("70")
	@Column(name = "credit_score", nullable = false)
	private Integer creditScore = 70;

	@ColumnDefault("'BASE'")
	@Column(name = "tier", nullable = false, length = 20)
	private String tier = "BASE";

	@Generated(event = EventType.INSERT)
	@ColumnDefault("(now())")
	@Column(name = "updated_at", nullable = false, columnDefinition = "datetime", insertable = false, updatable = true)
	private LocalDateTime updatedAt;

	public UserCredit(UserAccount user) {
		this.user = user;
	}
}
