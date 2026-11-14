package com.bts.fairticket.credit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bts.fairticket.credit.entity.UserCard;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {

    // 특정 카드가 몇 개의 고유 계정(UserAccount)에서 등록/사용되었는지 카운트
    @Query("SELECT COUNT(DISTINCT c.user.userId) FROM UserCard c WHERE c.cardHash = :cardHash")
    long countDistinctUserByCardHash(@Param("cardHash") String cardHash);

    // 유저가 등록한 모든 카드 목록 조회
    List<UserCard> findByUser_UserId(String userId);
}