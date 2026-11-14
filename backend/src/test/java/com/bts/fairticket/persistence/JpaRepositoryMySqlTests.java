package com.bts.fairticket.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.CreditHistoryRepository;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("mysql")
// validate는 읽기 전용 스키마 검사이며 테이블을 생성하거나 변경하지 않는다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional
class JpaRepositoryMySqlTests {

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private BehaviorEventRepository behaviorEventRepository;
	@Autowired
	private UserCreditRepository userCreditRepository;
	@Autowired
	private CreditHistoryRepository creditHistoryRepository;
	@Autowired
	private RiskAssessmentRepository riskAssessmentRepository;
	@Autowired
	private PolicyDecisionRepository policyDecisionRepository;

	@Test
	void savesAndLoadsAllEntitiesWithDefaultsRelationsAndJson() {
		UserAccount user = createUser();
		UserCredit credit = userCreditRepository.saveAndFlush(new UserCredit(user));
		Map<String, Object> metadata = Map.of(
				"bookingDurationSeconds", 12, "deviceChanged", true,
				"details", Map.of("source", "repository-test"), "tags", List.of("test", "json"));
		LocalDateTime occurredAt = LocalDateTime.of(2026, 1, 1, 12, 0);
		BehaviorEvent event = behaviorEventRepository.saveAndFlush(new BehaviorEvent(
				user, user.getServiceId(), UUID.randomUUID().toString(), "BOOKING",
				"test-booking", null, occurredAt, metadata));
		CreditHistory history = creditHistoryRepository.saveAndFlush(
				new CreditHistory(user, event, 70, 71, "repository integration test"));
		RiskAssessment assessment = riskAssessmentRepository.saveAndFlush(
				new RiskAssessment(user, 0.25, "test-model"));
		PolicyDecision decision = policyDecisionRepository.saveAndFlush(new PolicyDecision(
				user, assessment, "BOOKING_ATTEMPT", 71, 0.25, "ALLOW", "repository integration test"));
		entityManager.clear();

		UserAccount loadedUser = userAccountRepository.findById(user.getUserId()).orElseThrow();
		assertEquals("ACTIVE", loadedUser.getAccountStatus());
		assertNotNull(loadedUser.getCreatedAt());
		UserCredit loadedCredit = userCreditRepository.findById(user.getUserId()).orElseThrow();
		assertEquals(user.getUserId(), credit.getUserId());
		assertEquals(user.getUserId(), loadedCredit.getUser().getUserId());
		assertEquals(70, loadedCredit.getCreditScore());
		assertEquals("BASE", loadedCredit.getTier());
		assertNotNull(loadedCredit.getUpdatedAt());
		BehaviorEvent loadedEvent = behaviorEventRepository.findById(event.getEventId()).orElseThrow();
		assertEquals(metadata, loadedEvent.getMetadata());
		assertEquals(occurredAt, loadedEvent.getOccurredAt());
		assertEquals(user.getUserId(), loadedEvent.getUser().getUserId());
		assertNull(loadedEvent.getTicketId());
		CreditHistory loadedHistory = creditHistoryRepository.findById(history.getHistoryId()).orElseThrow();
		assertEquals(event.getEventId(), loadedHistory.getEvent().getEventId());
		assertEquals(user.getUserId(), loadedHistory.getUser().getUserId());
		assertEquals(70, loadedHistory.getScoreBefore());
		assertEquals(71, loadedHistory.getScoreAfter());
		assertNotNull(loadedHistory.getCreatedAt());
		RiskAssessment loadedAssessment = riskAssessmentRepository
				.findById(assessment.getAssessmentId()).orElseThrow();
		assertEquals(0.25, loadedAssessment.getAnomalyScore());
		assertEquals(user.getUserId(), loadedAssessment.getUser().getUserId());
		assertNotNull(loadedAssessment.getAssessedAt());
		PolicyDecision loadedDecision = policyDecisionRepository.findById(decision.getDecisionId()).orElseThrow();
		assertEquals(assessment.getAssessmentId(), loadedDecision.getAssessment().getAssessmentId());
		assertEquals(user.getUserId(), loadedDecision.getUser().getUserId());
		assertEquals("ALLOW", loadedDecision.getAction());
		assertNotNull(loadedDecision.getDecidedAt());
	}

	@Test
	void preservesNullableForeignKeysAndOptionalColumns() {
		UserAccount user = createUser();
		BehaviorEvent event = behaviorEventRepository.saveAndFlush(new BehaviorEvent(
				user, user.getServiceId(), UUID.randomUUID().toString(), "CHECK_IN",
				null, "test-ticket", LocalDateTime.of(2026, 1, 1, 12, 0), null));
		CreditHistory history = creditHistoryRepository.saveAndFlush(
				new CreditHistory(user, null, 70, 70, "test without related event"));
		PolicyDecision decision = policyDecisionRepository.saveAndFlush(new PolicyDecision(
				user, null, "BOOKING_ATTEMPT", 70, null, "ALLOW", null));
		entityManager.clear();

		BehaviorEvent loadedEvent = behaviorEventRepository.findById(event.getEventId()).orElseThrow();
		assertNull(loadedEvent.getBookingId());
		assertNull(loadedEvent.getMetadata());
		assertNull(creditHistoryRepository.findById(history.getHistoryId()).orElseThrow().getEvent());
		PolicyDecision loadedDecision = policyDecisionRepository.findById(decision.getDecisionId()).orElseThrow();
		assertNull(loadedDecision.getAssessment());
		assertNull(loadedDecision.getAnomalyScoreAtDecision());
		assertNull(loadedDecision.getReason());
	}

	private UserAccount createUser() {
		String id = UUID.randomUUID().toString();
		return userAccountRepository.saveAndFlush(new UserAccount(id, "repository-test", id));
	}
}
