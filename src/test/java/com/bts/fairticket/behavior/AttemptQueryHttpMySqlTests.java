package com.bts.fairticket.behavior;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.dto.EventType;
import com.bts.fairticket.behavior.service.AttemptQueryService;
import com.bts.fairticket.user.ApiKeyTestSupport;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
class AttemptQueryHttpMySqlTests {
    private static final String SERVICE_A = "attempt-it-" + UUID.randomUUID();
    private static final String SERVICE_B = "attempt-it-" + UUID.randomUUID();
    private static final String KEY_A = ApiKeyTestSupport.randomKey();
    private static final String KEY_B = ApiKeyTestSupport.randomKey();
    private static final String PATH = "/api/v1/events/booking-attempts";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("BTS_API_KEY_HASHES", () ->
                ApiKeyTestSupport.configuration(Map.of(SERVICE_A, KEY_A, SERVICE_B, KEY_B)));
    }

    @LocalServerPort private int port;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AttemptQueryService attempts;

    @AfterEach
    void cleanOnlyThisTestsFixtures() {
        jdbc.update("DELETE FROM behavior_event WHERE service_id IN (?, ?)", SERVICE_A, SERVICE_B);
        jdbc.update("DELETE FROM user_credit WHERE user_id IN "
                + "(SELECT user_id FROM user_account WHERE service_id IN (?, ?))", SERVICE_A, SERVICE_B);
        jdbc.update("DELETE FROM user_account WHERE service_id IN (?, ?)", SERVICE_A, SERVICE_B);
    }

    @Test
    void sharedAttemptIdHasSeparateUserAndConcertIdentitiesWithConsistentFiltersAndPages() throws Exception {
        String first = register(SERVICE_A, KEY_A);
        String second = register(SERVICE_A, KEY_A);
        String attempt = UUID.randomUUID().toString();
        postAttempt(SERVICE_A, KEY_A, first, attempt, "c1", "STARTED", "ENTERED", 1, "a-start", 201);
        postAttempt(SERVICE_A, KEY_A, second, attempt, "c1", "STARTED", "ENTERED", 1, "b-start", 201);
        postAttempt(SERVICE_A, KEY_A, first, attempt, "c2", "STARTED", "ENTERED", 1, "c-start", 201);

        assertQuery("", 3, 3);
        assertQuery("?concertId=c1", 2, 2);
        assertQuery("?userId=" + first, 2, 2);
        assertQuery("?concertId=c1&userId=" + first, 1, 1);
        assertQuery("?concertId=missing", 0, 0);
        assertQuery("?userId=" + UUID.randomUUID(), 0, 0);

        // All STARTED timestamps and attemptIds tie: pagination must still be stable and exhaustive.
        Set<String> identities = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            JsonNode data = assertQuery("?size=1&page=" + page, 3, 1);
            JsonNode item = data.get("items").get(0);
            String identity = identity(item);
            assertTrue(identities.add(identity), "A composite identity must not appear on two pages");
            assertEquals(identity, identity(assertQuery("?size=1&page=" + page, 3, 1).get("items").get(0)));
        }
        assertEquals(Set.of(first + "/c1/" + attempt, second + "/c1/" + attempt,
                first + "/c2/" + attempt), identities);
        assertQuery("?size=1&page=3", 3, 0);
        assertQuery("?concertId=c1&size=1&page=1", 2, 1);
        assertQuery("?concertId=c1&size=1&page=2", 2, 0);
        assertQuery("?userId=" + first + "&size=1&page=1", 2, 1);
        assertQuery("?concertId=c1&userId=" + first + "&size=1&page=1", 1, 0);

        assertEquals(3, attempts.count(SERVICE_A, null, null));
        assertEquals(2, attempts.count(SERVICE_A, "c1", null));
        assertEquals(2, attempts.count(SERVICE_A, null, first));
        assertEquals(1, attempts.count(SERVICE_A, "c1", first));
    }

    @Test
    void stagesAndReplaysDoNotInflateCountsAndLatestSequenceIsScopedToEachIdentity() throws Exception {
        String user = register(SERVICE_A, KEY_A);
        String otherUser = register(SERVICE_A, KEY_A);
        String foreignUser = register(SERVICE_B, KEY_B);
        String attempt = UUID.randomUUID().toString();

        postAttempt(SERVICE_A, KEY_A, user, attempt, "c1", "COMPLETED", "SUCCEEDED", 8, "complete", 201);
        assertQuery("", 0, 0); // A later stage alone does not establish a counted attempt.
        postAttempt(SERVICE_A, KEY_A, user, attempt, "c1", "STARTED", "ENTERED", 1, "start", 201);
        postAttempt(SERVICE_A, KEY_A, user, attempt, "c1", "STARTED", "ENTERED", 1, "start", 200);
        postAttempt(SERVICE_A, KEY_A, user, attempt, "c1", "STARTED", "ENTERED", 1, "duplicate-start", 201);
        postAttempt(SERVICE_A, KEY_A, user, attempt, "c1", "QUEUE", "ENTERED", 2, "late-queue", 201);
        JsonNode item = assertQuery("", 1, 1).get("items").get(0);
        assertEquals("COMPLETED", item.get("stage").asString());
        assertEquals("SUCCEEDED", item.get("status").asString());
        assertEquals(8, item.get("sequence").asInt());

        postAttempt(SERVICE_A, KEY_A, otherUser, attempt, "c1", "STARTED", "ENTERED", 1, "other-start", 201);
        postAttempt(SERVICE_A, KEY_A, otherUser, attempt, "c1", "QUEUE", "FAILED", 3, "other-queue", 201);
        postAttempt(SERVICE_A, KEY_A, user, attempt, "c2", "STARTED", "ENTERED", 1, "concert-start", 201);
        // Foreign service events share the attemptId and concert but must never affect service A.
        postAttempt(SERVICE_B, KEY_B, foreignUser, attempt, "c1", "STARTED", "ENTERED", 1, "foreign-start", 201);
        postAttempt(SERVICE_B, KEY_B, foreignUser, attempt, "c1", "PAYMENT", "FAILED", 99, "foreign-payment", 201);
        assertQuery("", 3, 3);
        JsonNode completed = assertQuery("?userId=" + user + "&concertId=c1", 1, 1).get("items").get(0);
        assertEquals("COMPLETED", completed.get("stage").asString());
        assertEquals(8, completed.get("sequence").asInt());
        JsonNode other = assertQuery("?userId=" + otherUser, 1, 1).get("items").get(0);
        assertEquals("QUEUE", other.get("stage").asString());
        assertEquals("FAILED", other.get("status").asString());
        JsonNode concert = assertQuery("?userId=" + user + "&concertId=c2", 1, 1).get("items").get(0);
        assertEquals("STARTED", concert.get("stage").asString());
        assertEquals(1, concert.get("sequence").asInt());
        assertQuery("?userId=" + foreignUser, 0, 0);
        JsonNode foreign = get(PATH, KEY_B, 200).get("data");
        assertEquals(1, foreign.get("bookingAttemptCount").asInt());
        assertEquals(1, foreign.get("items").size());
        assertEquals(99, foreign.get("items").get(0).get("sequence").asInt());
        assertEquals(1, attempts.count(SERVICE_B, null, null));
    }

    @Test
    void httpEndpointRejectsUnauthenticatedAndInvalidPaginationRequests() throws Exception {
        get(PATH, null, 401);
        get(PATH, ApiKeyTestSupport.randomKey(), 401);
        for (String query : new String[]{"?page=-1", "?page=100001", "?size=0", "?size=101", "?page=abc",
                "?concertId=" + "c".repeat(51), "?userId=" + "u".repeat(51)}) {
            get(PATH + query, KEY_A, 400);
        }
        assertQuery("", 0, 0);
    }

    private JsonNode assertQuery(String query, int total, int size) throws Exception {
        JsonNode response = get(PATH + query, KEY_A, 200);
        assertEquals("COMMON_200", response.get("code").asString());
        JsonNode data = response.get("data");
        assertEquals(total, data.get("bookingAttemptCount").asInt());
        assertEquals(size, data.get("items").size());
        for (JsonNode item : data.get("items")) {
            assertEquals(total, item.get("bookingAttemptCount").asInt());
        }
        return data;
    }

    private String identity(JsonNode item) {
        return item.get("userId").asString() + "/" + item.get("concertId").asString()
                + "/" + item.get("attemptId").asString();
    }

    private String register(String service, String key) throws Exception {
        return post("/api/v1/users", key, Map.of("serviceId", service,
                "externalUserId", UUID.randomUUID().toString()), 201).get("data").get("userId").asString();
    }

    private void postAttempt(String service, String key, String user, String attempt, String concert,
            String stage, String status, int sequence, String external, int expectedStatus) throws Exception {
        Map<String, Object> metadata = new HashMap<>(Map.of("attemptId", attempt, "concertId", concert,
                "transitionId", external, "stage", stage, "status", status, "sequence", sequence,
                "source", "SERVER", "recordedAt", "2026-11-08T00:00:00Z"));
        if (stage.equals("COMPLETED")) metadata.put("bookingId", "booking-1");
        post("/api/v1/events", key, new EventRequest(service, external, user, EventType.BOOKING_ATTEMPT,
                null, null, "2026-11-08T00:00:00Z", metadata), expectedStatus);
    }

    private JsonNode post(String path, String key, Object body, int status) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("X-API-Key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))), status);
    }

    private JsonNode get(String path, String key, int status) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).GET();
        if (key != null) request.header("X-API-Key", key);
        return send(request, status);
    }

    private JsonNode send(HttpRequest.Builder request, int status) throws Exception {
        var response = client.send(request.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
