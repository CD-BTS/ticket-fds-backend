package com.bts.fairticket.behavior.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Count STARTED identities, and restore progress by sequence rather than delivery order. */
@Service
public class AttemptQueryService {
    private final JdbcTemplate jdbc;
    public AttemptQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long count(String service,String concert,String user) {
        Long count=jdbc.queryForObject("""
            SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.attemptId')))
            FROM behavior_event WHERE service_id=? AND event_type='BOOKING_ATTEMPT'
              AND JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.stage'))='STARTED'
              AND JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.status'))='ENTERED'
              AND (? IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.concertId'))=?)
              AND (? IS NULL OR user_id=?)
            """,Long.class,service,concert,concert,user,user);
        return count==null?0:count;
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> find(String service, String concert, String user, int offset, int limit) {
        return jdbc.queryForList("""
            WITH events AS (
                SELECT event_id,user_id,occurred_at,metadata,
                    JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.attemptId')) AS attempt_id,
                    JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.concertId')) AS concert_id,
                    JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.stage')) AS stage,
                    CAST(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.sequence')) AS UNSIGNED) AS seq
                FROM behavior_event WHERE service_id=? AND event_type='BOOKING_ATTEMPT'
            ), started AS (
                SELECT user_id,attempt_id,concert_id,MIN(occurred_at) AS started_at
                FROM events WHERE stage='STARTED' AND JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.status'))='ENTERED'
                GROUP BY user_id,attempt_id,concert_id
            ), ranked AS (
                SELECT e.*,ROW_NUMBER() OVER (PARTITION BY user_id,attempt_id,concert_id ORDER BY seq DESC,event_id DESC) AS rn
                FROM events e
            )
            SELECT s.attempt_id AS attemptId,s.concert_id AS concertId,s.user_id AS userId,s.started_at AS startedAt,
                r.seq AS sequence,JSON_UNQUOTE(JSON_EXTRACT(r.metadata,'$.stage')) AS stage,
                JSON_UNQUOTE(JSON_EXTRACT(r.metadata,'$.status')) AS status,
                COUNT(*) OVER () AS bookingAttemptCount
            FROM started s JOIN ranked r ON r.user_id=s.user_id AND r.attempt_id=s.attempt_id AND r.concert_id=s.concert_id AND r.rn=1
            WHERE (? IS NULL OR s.concert_id=?) AND (? IS NULL OR s.user_id=?)
            ORDER BY s.started_at DESC,s.attempt_id LIMIT ? OFFSET ?
            """, service,concert,concert,user,user,limit,offset);
    }
}
