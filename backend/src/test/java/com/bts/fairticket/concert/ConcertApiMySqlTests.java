package com.bts.fairticket.concert;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.bts.fairticket.user.ApiKeyTestSupport;
import com.bts.fairticket.behavior.service.AttemptQueryService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Transactional
class ConcertApiMySqlTests {
    private static final String A = "concert-" + UUID.randomUUID();
    private static final String B = "concert-" + UUID.randomUUID();
    private static final String KEY = ApiKeyTestSupport.randomKey();
    private static final String OTHER_KEY = ApiKeyTestSupport.randomKey();
    private static final String ADMIN = ApiKeyTestSupport.randomKey();
    private static final String GLOBAL = ApiKeyTestSupport.randomKey();
    private static final String PATH = "/api/v1/admin/concerts";
    private static final String BODY = """
            {"name":"Concert","startsAt":"2026-12-01T19:00:00+09:00",
             "endsAt":"2026-12-01T21:00:00+09:00","venue":"Hall"}
            """;
    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("BTS_API_KEY_HASHES", () -> ApiKeyTestSupport.configuration(Map.of(A, KEY, B, OTHER_KEY)));
        registry.add("BTS_ADMIN_API_KEYS", () -> "[{\"hash\":\"" + ApiKeyTestSupport.hash(ADMIN)
                + "\",\"services\":[\"" + A + "\"],\"allServices\":false},{\"hash\":\""
                + ApiKeyTestSupport.hash(GLOBAL) + "\",\"services\":[],\"allServices\":true}]");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired AttemptQueryService attempts;

    private void putConcert(String id, String key, String body) throws Exception {
        mvc.perform(put("/api/v1/concerts/" + id).header("X-API-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isOk());
    }
    private String user(String service, String external, int score) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into user_account(user_id,service_id,external_user_id) values (?,?,?)", id, service, external);
        jdbc.update("insert into user_credit(user_id,credit_score) values (?,?)", id, score);
        return id;
    }
    private void event(String user, String service, String concert, String attempt, String stage, String type, int sequence) throws Exception {
        jdbc.update("insert into behavior_event(user_id,service_id,external_event_id,event_type,occurred_at,metadata) values (?,?,?,?,'2026-11-10 10:00:00',?)",
                user, service, UUID.randomUUID().toString(), type, json.writeValueAsString(Map.of(
                    "concertId", concert, "attemptId", attempt, "stage", stage, "status", "ENTERED", "sequence", sequence)));
    }

    @Test
    void putIsIdempotentUpdatesAndNormalizesUtcWithoutCrossServiceWrites() throws Exception {
        putConcert("shared", KEY, BODY);
        putConcert("shared", KEY, BODY);
        assertEquals(1, jdbc.queryForObject("select count(*) from concert where service_id=?", Integer.class, A));
        putConcert("shared", OTHER_KEY, BODY.replace("Concert", "Foreign"));
        putConcert("shared", KEY, BODY.replace("Concert", "Updated"));
        mvc.perform(get(PATH + "/shared").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.concert.name").value("Updated"))
                .andExpect(jsonPath("data.concert.startsAt").value("2026-12-01T10:00:00Z"))
                .andExpect(jsonPath("data.concert.endsAt").value("2026-12-01T12:00:00Z"));
        mvc.perform(get(PATH + "/shared").param("serviceId", B).header("X-API-Key", GLOBAL))
                .andExpect(status().isOk()).andExpect(jsonPath("data.concert.name").value("Foreign"));
    }

    @Test
    void listPagesOnlyRegisteredConcertsAndHonorsRequiredScope() throws Exception {
        putConcert("a", KEY, BODY); putConcert("b", KEY, BODY); putConcert("a", OTHER_KEY, BODY);
        mvc.perform(get(PATH).param("serviceId", A).param("sort", "concertId,asc").param("size", "1").header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2))
                .andExpect(jsonPath("data.hasNext").value(true)).andExpect(jsonPath("data.content[0].concertId").value("a"));
        mvc.perform(get(PATH).param("serviceId", A).param("sort", "concertId,asc").param("size", "1").param("page", "1").header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content[0].concertId").value("b"));
        mvc.perform(get(PATH).param("serviceId", "empty").header("X-API-Key", GLOBAL))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty());
        mvc.perform(get(PATH).param("serviceId", B).header("X-API-Key", ADMIN)).andExpect(status().isForbidden());
    }

    @Test
    void statisticsCountStartedCompositeAttemptsAndUsersWithoutEventInflation() throws Exception {
        putConcert("c", KEY, BODY);
        String first = user(A, "one", 80), second = user(A, "two", 40), foreign = user(B, "other", 100);
        event(first, A, "c", "attempt-1", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "c", "attempt-1", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "c", "attempt-1", "PAYMENT", "BOOKING_ATTEMPT", 8);
        event(first, A, "c", "attempt-2", "STARTED", "BOOKING_ATTEMPT", 1);
        event(second, A, "c", "attempt-1", "STARTED", "BOOKING_ATTEMPT", 1);
        event(second, A, "elsewhere", "attempt-1", "STARTED", "BOOKING_ATTEMPT", 1);
        event(foreign, B, "c", "attempt-1", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "c", "booking", "STARTED", "BOOKING", 1);
        event(first, A, "c", "cancel", "STARTED", "CANCEL", 1);
        assertEquals(3, attempts.count(A, "c", null));
        event(foreign, A, "c", "mislinked", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "C", "other-case", "STARTED", "BOOKING_ATTEMPT", 1);
        event(second, A, "c", "not-entered", "STARTED", "BOOKING_ATTEMPT", 1);
        jdbc.update("update behavior_event set metadata=JSON_SET(metadata,'$.status','COMPLETED') where user_id=? and JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.attemptId'))='not-entered'", second);
        mvc.perform(get(PATH + "/c").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.bookingAttemptCount").value(3))
                .andExpect(jsonPath("data.bookingAttemptUserCount").value(2));
    }

    @Test
    void usersAreDeduplicatedFilteredAndEnrichedWithLatestGlobalAssessmentAndConcertActivity() throws Exception {
        putConcert("c", KEY, BODY);
        String first = user(A, "one", 80), second = user(A, "two", 40), orphan = user(A, "orphan", 90);
        event(first, A, "c", "a", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "c", "b", "STARTED", "BOOKING_ATTEMPT", 1);
        event(first, A, "c", "a", "PAYMENT", "BOOKING_ATTEMPT", 2);
        event(second, A, "c", "a", "STARTED", "BOOKING_ATTEMPT", 1);
        event(orphan, A, "c", "a", "PAYMENT", "BOOKING_ATTEMPT", 2);
        jdbc.update("update behavior_event set occurred_at='2026-11-10 11:00:00' where user_id=? and JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.stage'))='PAYMENT'", first);
        event(first, A, "elsewhere", "other-concert", "PAYMENT", "BOOKING_ATTEMPT", 3);
        jdbc.update("update behavior_event set occurred_at='2026-11-10 12:00:00' where user_id=? and JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.concertId'))='elsewhere'", first);
        jdbc.update("insert into risk_assessment(user_id,anomaly_score,model_version,assessed_at) values (?,0.2,'v1','2026-11-09 00:00:00'),(?,0.8,'v2','2026-11-10 00:00:00')", first, first);
        mvc.perform(get(PATH + "/c/users").param("serviceId", A).param("sort", "creditScore,desc").param("size", "1").header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2))
                .andExpect(jsonPath("data.content[0].user.userId").value(first))
                .andExpect(jsonPath("data.content[0].user.creditScore").value(80))
                .andExpect(jsonPath("data.content[0].user.anomalyScore").value(0.8))
                .andExpect(jsonPath("data.content[0].lastBookingAttemptAt").value("2026-11-10T11:00:00Z"));
        mvc.perform(get(PATH + "/c/users").param("serviceId", A).param("externalUserId", "two").param("maxCreditScore", "50").header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1))
                .andExpect(jsonPath("data.content[0].user.userId").value(second))
                .andExpect(jsonPath("data.content[0].user.anomalyScore").isEmpty());
        mvc.perform(get(PATH + "/c/users").param("serviceId", A).param("page", "9").header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2)).andExpect(jsonPath("data.content").isEmpty());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void unregisteredConcertEventsAreAcceptedAndAppearAfterRegistration() throws Exception {
        String id = user(A, "early", 70);
        try {
        var metadata = Map.of("attemptId", UUID.randomUUID().toString(), "concertId", "early", "transitionId", "start",
                "stage", "STARTED", "status", "ENTERED", "sequence", 1, "source", "SERVER", "recordedAt", "2026-11-10T00:00:00Z");
        mvc.perform(post("/api/v1/events").header("X-API-Key", KEY).contentType("application/json")
                .content(json.writeValueAsString(Map.of("serviceId", A, "externalEventId", "early", "userId", id,
                        "eventType", "BOOKING_ATTEMPT", "occurredAt", "2026-11-10T00:00:00Z", "metadata", metadata))))
                .andExpect(status().isCreated());
        for (String suffix : List.of("", "/users")) {
            mvc.perform(get(PATH + "/early" + suffix).param("serviceId", A).header("X-API-Key", ADMIN))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("CONCERT_NOT_FOUND"));
        }
        mvc.perform(get(PATH).param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(0));
        putConcert("early", KEY, BODY);
        mvc.perform(get(PATH + "/early").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.bookingAttemptCount").value(1));
        mvc.perform(get(PATH + "/early/users").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1));
        } finally {
            jdbc.update("delete from behavior_event where user_id=?", id);
            jdbc.update("delete from user_credit where user_id=?", id);
            jdbc.update("delete from user_account where user_id=?", id);
            jdbc.update("delete from concert where service_id=? and concert_id='early'", A);
        }
    }

    @Test
    void emptyAndMissingConcertsHaveDistinctResponses() throws Exception {
        putConcert("empty", KEY, BODY);
        mvc.perform(get(PATH + "/empty").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.bookingAttemptCount").value(0))
                .andExpect(jsonPath("data.bookingAttemptUserCount").value(0));
        mvc.perform(get(PATH + "/empty/users").param("serviceId", A).header("X-API-Key", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty());
        mvc.perform(get(PATH + "/missing").param("serviceId", A).header("X-API-Key", ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void authenticationScopesAndServiceCaseAreEnforcedOnEveryNewRoute() throws Exception {
        for (String path : List.of(PATH, PATH + "/c", PATH + "/c/users")) {
            mvc.perform(get(path).param("serviceId", A)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).param("serviceId", A).header("X-API-Key", KEY)).andExpect(status().isForbidden());
            mvc.perform(get(path).param("serviceId", B).header("X-API-Key", ADMIN)).andExpect(status().isForbidden());
            mvc.perform(get(path).header("X-API-Key", GLOBAL)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/concerts/c").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/concerts/c").header("X-API-Key", ADMIN).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        putConcert("c", KEY, BODY);
        mvc.perform(get(PATH).param("serviceId", A.toUpperCase(Locale.ROOT)).header("X-API-Key", GLOBAL))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content").isEmpty());
        mvc.perform(get(PATH + "/C").param("serviceId", A).header("X-API-Key", ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void invalidTimeIdentifiersFiltersAndPagingAreRejected() throws Exception {
        for (String body : List.of(BODY.replace("+09:00", ""), BODY.replace("19:00:00", "22:00:00"),
                BODY.replace("19:00:00", "19:00:00.001"), BODY.replace("Concert", ""))) {
            mvc.perform(put("/api/v1/concerts/c").header("X-API-Key", KEY).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        putConcert("c", KEY, BODY);
        for (String suffix : List.of("", "/c/users")) {
            for (String[] param : new String[][]{{"page","-1"},{"size","101"},{"sort","unknown,desc"}}) {
                mvc.perform(get(PATH + suffix).param("serviceId", A).param(param[0], param[1]).header("X-API-Key", ADMIN))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(get(PATH + "/c/users").param("serviceId", A).param("minCreditScore", "90").param("maxCreditScore", "20").header("X-API-Key", ADMIN))
                .andExpect(status().isBadRequest());
    }
}
