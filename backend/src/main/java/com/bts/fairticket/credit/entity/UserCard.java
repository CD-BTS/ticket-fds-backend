package com.bts.fairticket.credit.entity;

import java.time.LocalDateTime;

import com.bts.fairticket.user.entity.UserAccount;

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

@Entity
@Table(name = "user_card", indexes = {
        @Index(name = "idx_user_card_hash", columnList = "card_hash"),
        @Index(name = "idx_user_card_user", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "card_hash", nullable = false, length = 64)
    private String cardHash;

    @Column(name = "card_company", length = 30)
    private String cardCompany;

    @Column(name = "masked_card_no", length = 20)
    private String maskedCardNo;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UserCard(UserAccount user, String cardHash, String cardCompany, String maskedCardNo) {
        this.user = user;
        this.cardHash = cardHash;
        this.cardCompany = cardCompany;
        this.maskedCardNo = maskedCardNo;
    }
}