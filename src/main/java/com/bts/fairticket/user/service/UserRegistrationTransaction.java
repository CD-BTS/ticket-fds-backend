package com.bts.fairticket.user.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;

@Service
public class UserRegistrationTransaction {

	private final UserAccountRepository userRepository;
	private final UserCreditRepository creditRepository;

	public UserRegistrationTransaction(UserAccountRepository userRepository,
			UserCreditRepository creditRepository) {
		this.userRepository = userRepository;
		this.creditRepository = creditRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public RegistrationResult register(UserRegistrationRequest request) {
		Optional<UserAccount> existing = findAccount(request);
		if (existing.isPresent()) {
			return existingResult(existing.get());
		}
		UserAccount user = userRepository.saveAndFlush(new UserAccount(
				UUID.randomUUID().toString(), request.serviceId(), request.externalUserId()));
		UserCredit credit = creditRepository.saveAndFlush(new UserCredit(user));
		return new RegistrationResult(UserResponse.from(user, credit), true);
	}

	// UNIQUE 충돌의 롤백이 끝난 뒤 별도 트랜잭션에서 승자의 커밋된 데이터를 조회한다.
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
			readOnly = true)
	public Optional<RegistrationResult> findExisting(UserRegistrationRequest request) {
		return findAccount(request).map(this::existingResult);
	}

	private Optional<UserAccount> findAccount(UserRegistrationRequest request) {
		return userRepository.findByServiceIdAndExternalUserId(request.serviceId(), request.externalUserId())
				.filter(user -> user.getServiceId().equals(request.serviceId()));
	}

	private RegistrationResult existingResult(UserAccount user) {
		UserCredit credit = creditRepository.findById(user.getUserId())
				.orElseThrow(() -> new ApiException(ResponseCode.INTERNAL_SERVER_ERROR));
		return new RegistrationResult(UserResponse.from(user, credit), false);
	}

	public record RegistrationResult(UserResponse user, boolean created) {
	}
}
