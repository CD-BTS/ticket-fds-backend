package com.bts.fairticket.user.service;

import java.util.Optional;
import java.time.LocalDateTime;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;
import com.bts.fairticket.user.service.UserRegistrationTransaction.RegistrationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTests {

	private final UserRegistrationTransaction transaction = mock(UserRegistrationTransaction.class);
	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final UserCreditRepository credits = mock(UserCreditRepository.class);
	private final RiskAssessmentRepository assessments = mock(RiskAssessmentRepository.class);
	private final PolicyDecisionRepository policies = mock(PolicyDecisionRepository.class);
	private UserService service;
	private final UserRegistrationRequest request = new UserRegistrationRequest("service-a", "external-1");

	@BeforeEach
	void setUp() {
		service = new UserService(transaction, users, credits, assessments, policies);
	}

	@Test
	void rejectsServiceMismatchBeforeAccessingDatabase() {
		ApiException exception = assertThrows(ApiException.class, () -> service.register(request, "service-b"));
		assertEquals(ResponseCode.FORBIDDEN, exception.getResponseCode());
		verifyNoInteractions(transaction, users, credits);
	}

	@Test
	void returnsCommittedWinnerAfterUniqueViolation() {
		UserAccount user = new UserAccount("id", "service-a", "external-1");
		var existing = new RegistrationResult(UserResponse.from(user, new UserCredit(user)), false);
		when(transaction.register(request)).thenThrow(new DataIntegrityViolationException("conflict"));
		when(transaction.findExisting(request)).thenReturn(Optional.of(existing));
		assertEquals(existing, service.register(request, "service-a"));
	}

	@Test
	void reportsUnresolvedConstraintViolationAsConflict() {
		when(transaction.register(request)).thenThrow(new DataIntegrityViolationException("internal database detail"));
		when(transaction.findExisting(request)).thenReturn(Optional.empty());
		ApiException exception = assertThrows(ApiException.class, () -> service.register(request, "service-a"));
		assertEquals(ResponseCode.DATA_CONFLICT, exception.getResponseCode());
	}

	@Test
	void hidesOtherServiceUserAndHandlesMissingUser() {
		when(users.findById("other")).thenReturn(Optional.of(new UserAccount("other", "service-b", "external")));
		when(users.findById("missing")).thenReturn(Optional.empty());
		for (String id : new String[] {"other", "missing"}) {
			ApiException exception = assertThrows(ApiException.class, () -> service.getUser(id, "service-a"));
			assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
		}
		verifyNoInteractions(credits, assessments, policies);
	}

	private void stubOwnUser() {
		var user = new UserAccount("id", "service-a", "external");
		when(users.findById("id")).thenReturn(Optional.of(user));
		when(credits.findById("id")).thenReturn(Optional.of(new UserCredit(user)));
	}

	@Test
	void noHistoryReturnsNullSummaryWithoutChangingBasicFields() {
		stubOwnUser();
		var result = service.getUser("id", "service-a");
		assertEquals("id", result.userId());
		assertEquals(70, result.creditScore());
		assertEquals("BASE", result.tier());
		assertNull(result.anomalyScore());
		assertNull(result.lastAssessedAt());
		assertNull(result.latestAction());
	}

	@Test
	void assessmentOnlyReturnsStoredScoreAndTime() {
		stubOwnUser();
		var assessment = mock(RiskAssessment.class);
		var time = LocalDateTime.of(2026, 10, 10, 12, 0);
		when(assessment.getAnomalyScore()).thenReturn(0.82);
		when(assessment.getAssessedAt()).thenReturn(time);
		when(assessments.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc("id"))
				.thenReturn(Optional.of(assessment));
		var result = service.getUser("id", "service-a");
		assertEquals(0.82, result.anomalyScore());
		assertEquals(time, result.lastAssessedAt());
		assertNull(result.latestAction());
	}

	@Test
	void policyOnlyReturnsStoredActionWithoutInventingAnAssessment() {
		stubOwnUser();
		var policy = mock(PolicyDecision.class);
		when(policy.getAction()).thenReturn("DELAY");
		when(policies.findFirstByUser_UserIdOrderByDecidedAtDescDecisionIdDesc("id"))
				.thenReturn(Optional.of(policy));
		var result = service.getUser("id", "service-a");
		assertEquals("DELAY", result.latestAction());
		assertNull(result.anomalyScore());
		assertNull(result.lastAssessedAt());
	}
}
