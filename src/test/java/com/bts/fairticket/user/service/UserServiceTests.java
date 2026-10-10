package com.bts.fairticket.user.service;

import java.util.Optional;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTests {

	private final UserRegistrationTransaction transaction = mock(UserRegistrationTransaction.class);
	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final UserCreditRepository credits = mock(UserCreditRepository.class);
	private UserService service;
	private final UserRegistrationRequest request = new UserRegistrationRequest("service-a", "external-1");

	@BeforeEach
	void setUp() {
		service = new UserService(transaction, users, credits);
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
		assertEquals(ResponseCode.CONFLICT, exception.getResponseCode());
	}

	@Test
	void hidesOtherServiceUserAndHandlesMissingUser() {
		when(users.findById("other")).thenReturn(Optional.of(new UserAccount("other", "service-b", "external")));
		when(users.findById("missing")).thenReturn(Optional.empty());
		for (String id : new String[] {"other", "missing"}) {
			ApiException exception = assertThrows(ApiException.class, () -> service.getUser(id, "service-a"));
			assertEquals(ResponseCode.NOT_FOUND, exception.getResponseCode());
		}
		verifyNoInteractions(credits);
	}
}
