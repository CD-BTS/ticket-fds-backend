package com.bts.fairticket.concert.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concert", uniqueConstraints = @UniqueConstraint(name = "concert_service_concert",
        columnNames = {"service_id", "concert_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;
    @Column(name = "concert_id", nullable = false, length = 50)
    private String concertId;
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    @Column(name = "starts_at", nullable = false, columnDefinition = "datetime")
    private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false, columnDefinition = "datetime")
    private LocalDateTime endsAt;
    @Column(name = "venue", nullable = false, length = 255)
    private String venue;
}
