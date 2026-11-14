package com.bts.fairticket.credit.entity;

import java.time.LocalDateTime;

import com.bts.fairticket.user.entity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "booking_history", indexes = {
        @Index(name = "idx_booking_user_status", columnList = "user_id, status"),
        @Index(name = "idx_booking_user_event", columnList = "user_id, event_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "booking_reference", nullable = false, length = 50)
    private String bookingReference;

    @Column(name = "event_id", length = 50)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BookingHistory(UserAccount user, String bookingReference, String eventId,
                          BookingStatus status, LocalDateTime bookedAt, LocalDateTime eventAt) {
        this.user = user;
        this.bookingReference = bookingReference;
        this.eventId = eventId;
        this.status = status;
        this.bookedAt = bookedAt;
        this.eventAt = eventAt;
    }

    public enum BookingStatus {
        COMPLETED, NO_SHOW, AUTO_CANCELLED, CANCELLED
    }
}