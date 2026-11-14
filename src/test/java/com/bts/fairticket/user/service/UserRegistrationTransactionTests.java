package com.bts.fairticket.user.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationTransactionTests {
	@Mock private UserAccountRepository users;
	@Mock private UserCreditRepository credits;

	@ParameterizedTest
	@ValueSource(strings = {"ACTIVE", "SUSPENDED"})
	void createsNewAccountWithRequestedStatusAndInitialCredit(String status) {
		when(users.findByServiceIdAndExternalUserId("service", "external")).thenReturn(Optional.empty());
		when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(credits.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		var result = new UserRegistrationTransaction(users, credits)
				.register(new UserRegistrationRequest("service", "external", status));
		assertTrue(result.created());
		assertEquals(status, result.user().accountStatus());
		assertEquals(70, result.user().creditScore());
		assertEquals("BASE", result.user().tier());
		assertDoesNotThrow(() -> java.util.UUID.fromString(result.user().userId()));
		var order = inOrder(users, credits);
		order.verify(users).findByServiceIdAndExternalUserId("service", "external");
		order.verify(users).saveAndFlush(any(UserAccount.class));
		order.verify(credits).saveAndFlush(any(UserCredit.class));
	}

	@Test
	void reRegistrationDoesNotSaveAccountOrCreditOrApplyRequestedStatus() {
		var existing = new UserAccount("id", "service", "external", "SUSPENDED");
		when(users.findByServiceIdAndExternalUserId("service", "external")).thenReturn(Optional.of(existing));
		when(credits.findById("id")).thenReturn(Optional.of(new UserCredit(existing)));
		var result = new UserRegistrationTransaction(users, credits)
				.register(new UserRegistrationRequest("service", "external", "ACTIVE"));
		assertFalse(result.created());
		assertEquals("id", result.user().userId());
		assertEquals("SUSPENDED", result.user().accountStatus());
		verify(users, never()).saveAndFlush(any());
		verify(credits, never()).saveAndFlush(any());
	}
}
