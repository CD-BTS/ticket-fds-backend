package com.bts.fairticket.concert.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.bts.fairticket.common.query.QueryPage;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.concert.dto.*;

@Repository
public class ConcertRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SERVICE = "service_id = :serviceId and hex(service_id) = hex(:serviceId)";
    private static final String IDENTITY = SERVICE + " and concert_id = :concertId and hex(concert_id) = hex(:concertId)";
    public ConcertRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsert(String serviceId, String concertId, ConcertRequest request) {
        var params = new HashMap<String, Object>(Map.of("serviceId", serviceId, "concertId", concertId,
                "name", request.name(), "venue", request.venue(),
                "startsAt", request.startsAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                "endsAt", request.endsAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()));
        jdbc.update("""
                insert into concert(service_id,concert_id,name,starts_at,ends_at,venue)
                values (:serviceId,:concertId,:name,:startsAt,:endsAt,:venue)
                on duplicate key update name=:name, starts_at=:startsAt, ends_at=:endsAt, venue=:venue
                """, params);
    }

    public Optional<ConcertResponse> find(String serviceId, String concertId) {
        return jdbc.query("select * from concert where " + IDENTITY,
                Map.of("serviceId", serviceId, "concertId", concertId), this::map).stream().findFirst();
    }

    public PageResponse<ConcertResponse> list(String serviceId, ConcertQuery filter) {
        var page = QueryPage.parse(filter.page(), filter.size(), filter.sort(), Map.of(
                "startsAt", "starts_at", "concertId", "concert_id", "name", "name"), "startsAt", "concert_id");
        var params = Map.<String, Object>of("serviceId", serviceId, "limit", page.size(), "offset", page.offset());
        long total = jdbc.queryForObject("select count(*) from concert where " + SERVICE, params, Long.class);
        var content = jdbc.query("select * from concert where " + SERVICE + " order by " + page.orderBy()
                + " limit :limit offset :offset", params, this::map);
        return PageResponse.of(content, page.page(), page.size(), total);
    }

    /** Same STARTED/ENTERED composite identity as AttemptQueryService; no event-row counting. */
    public long[] statistics(String serviceId, String concertId) {
        return jdbc.queryForObject("""
                with started as (
                    select e.user_id, JSON_UNQUOTE(JSON_EXTRACT(e.metadata,'$.attemptId')) as attempt_id,
                        JSON_UNQUOTE(JSON_EXTRACT(e.metadata,'$.concertId')) as concert_id
                    from behavior_event e join user_account u on u.user_id=e.user_id
                    where e.service_id=:serviceId and hex(e.service_id)=hex(:serviceId)
                    and hex(u.service_id)=hex(:serviceId) and e.event_type='BOOKING_ATTEMPT'
                    and hex(JSON_UNQUOTE(JSON_EXTRACT(e.metadata,'$.concertId')))=hex(:concertId)
                    and JSON_UNQUOTE(JSON_EXTRACT(e.metadata,'$.stage'))='STARTED'
                    and JSON_UNQUOTE(JSON_EXTRACT(e.metadata,'$.status'))='ENTERED'
                    group by e.user_id, attempt_id, concert_id
                ) select count(*) as attempts, count(distinct user_id) as users from started
                """, Map.of("serviceId", serviceId, "concertId", concertId),
                (rs, row) -> new long[]{rs.getLong("attempts"), rs.getLong("users")});
    }

    public Map<String, OffsetDateTime> lastActivities(String serviceId, String concertId, List<String> users) {
        if (users.isEmpty()) return Map.of();
        var result = new HashMap<String, OffsetDateTime>();
        jdbc.query("""
                select user_id, max(occurred_at) as last_at from behavior_event
                where service_id=:serviceId and hex(service_id)=hex(:serviceId)
                and event_type='BOOKING_ATTEMPT' and user_id in (:users)
                and hex(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.concertId')))=hex(:concertId)
                group by user_id
                """, Map.of("serviceId", serviceId, "concertId", concertId, "users", users),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getString("user_id"), utc(rs, "last_at")));
        return result;
    }

    private ConcertResponse map(ResultSet rs, int row) throws SQLException {
        return new ConcertResponse(rs.getString("service_id"), rs.getString("concert_id"), rs.getString("name"),
                utc(rs, "starts_at"), utc(rs, "ends_at"), rs.getString("venue"));
    }
    private OffsetDateTime utc(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).atOffset(ZoneOffset.UTC);
    }
}
