package com.bts.fairticket.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.credit.repository.UserCreditRepository;
import com.bts.fairticket.user.dto.UserRegistrationRequest;
import com.bts.fairticket.user.dto.UserResponse;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;
import com.bts.fairticket.user.service.UserRegistrationTransaction.RegistrationResult;

@Service
public class UserService {

	private final UserRegistrationTransaction registrationTransaction;
	private final UserAccountRepository userRepository;
	private final UserCreditRepository creditRepository;

	public UserService(UserRegistrationTransaction registrationTransaction,
			UserAccountRepository userRepository, UserCreditRepository creditRepository) {
		this.registrationTransaction = registrationTransaction;
		this.userRepository = userRepository;
		this.creditRepository = creditRepository;
	}

	public RegistrationResult register(UserRegistrationRequest request, String authenticatedService) {
		if (!authenticatedService.equals(request.serviceId())) {
			throw new ApiException(ResponseCode.FORBIDDEN);
		}
		try {
			return registrationTransaction.register(request);
		} catch (DataIntegrityViolationException exception) {
			return registrationTransaction.findExisting(request)
					.orElseThrow(() -> new ApiException(ResponseCode.CONFLICT));
		}
	}

	@Transactional(readOnly = true)
	public UserResponse getUser(String userId, String authenticatedService) {
		UserAccount user = userRepository.findById(userId)
				.filter(account -> account.getServiceId().equals(authenticatedService))
				.orElseThrow(() -> new ApiException(ResponseCode.NOT_FOUND));
		UserCredit credit = creditRepository.findById(user.getUserId())
				.orElseThrow(() -> new ApiException(ResponseCode.INTERNAL_SERVER_ERROR));
		return UserResponse.from(user, credit);
	}
}
