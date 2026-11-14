package com.bts.fairticket.credit.repository;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bts.fairticket.credit.entity.CreditHistory;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {

    @Query("SELECT ch FROM CreditHistory ch WHERE ch.user.userId = :userId " +
           "AND (:startDateTime IS NULL OR ch.createdAt >= :startDateTime) " +
           "AND (:endDateTime IS NULL OR ch.createdAt <= :endDateTime) " +
           "ORDER BY ch.createdAt DESC, ch.historyId DESC")
    Page<CreditHistory> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable
    );
}