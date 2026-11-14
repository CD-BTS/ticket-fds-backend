package com.bts.fairticket.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bts.fairticket.credit.entity.BookingHistory;
import com.bts.fairticket.credit.entity.BookingHistory.BookingStatus;

public interface BookingHistoryRepository extends JpaRepository<BookingHistory, Long> {

    long countByUser_UserIdAndStatus(String userId, BookingStatus status);

    // 관람 이력 한 번에 카운트하는 프로젝션 인터페이스
    @Query("""
        SELECT 
            SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,
            SUM(CASE WHEN b.status = 'NO_SHOW' THEN 1 ELSE 0 END) AS noShowCount,
            SUM(CASE WHEN b.status = 'AUTO_CANCELLED' THEN 1 ELSE 0 END) AS autoCancelledCount
        FROM BookingHistory b
        WHERE b.user.userId = :userId
    """)
    BookingStats getBookingStatsByUserId(@Param("userId") String userId);

    interface BookingStats {
        Long getCompletedCount();
        Long getNoShowCount();
        Long getAutoCancelledCount();
    }
}