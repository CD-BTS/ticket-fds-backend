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
import com.bts.fairticket.user.dto.UserDetailResponse;
import com.bts.fairticket.risk.repository.RiskAssessmentRepository;
import com.bts.fairticket.policy.repository.PolicyDecisionRepository;
import com.bts.fairticket.user.entity.UserAccount;
import com.bts.fairticket.user.repository.UserAccountRepository;
import com.bts.fairticket.user.service.UserRegistrationTransaction.RegistrationResult;

@Service
public class UserService {

	private final UserRegistrationTransaction registrationTransaction;
	private final UserAccountRepository userRepository;
	private final UserCreditRepository creditRepository;
	private final RiskAssessmentRepository assessmentRepository;
	private final PolicyDecisionRepository policyRepository;

	public UserService(UserRegistrationTransaction registrationTransaction,
			UserAccountRepository userRepository, UserCreditRepository creditRepository,
			RiskAssessmentRepository assessmentRepository, PolicyDecisionRepository policyRepository) {
		this.registrationTransaction = registrationTransaction;
		this.userRepository = userRepository;
		this.creditRepository = creditRepository;
		this.assessmentRepository = assessmentRepository;
		this.policyRepository = policyRepository;
	}

	public RegistrationResult register(UserRegistrationRequest request, String authenticatedService) {
		if (!authenticatedService.equals(request.serviceId())) {
			throw new ApiException(ResponseCode.FORBIDDEN);
		}
		try {
			return registrationTransaction.register(request);
		} catch (DataIntegrityViolationException exception) {
			return registrationTransaction.findExisting(request)
					.orElseThrow(() -> new ApiException(ResponseCode.DATA_CONFLICT));
		}
	}

	@Transactional(readOnly = true)
	public UserDetailResponse getUser(String userId, String authenticatedService) {
		UserAccount user = userRepository.findById(userId)
				.filter(account -> account.getServiceId().equals(authenticatedService))
				.orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));
		UserCredit credit = creditRepository.findById(user.getUserId())
				.orElseThrow(() -> new ApiException(ResponseCode.INTERNAL_SERVER_ERROR));
		var assessment = assessmentRepository
				.findFirstByUser_UserIdOrderByAssessedAtDescAssessmentIdDesc(user.getUserId()).orElse(null);
		var policy = policyRepository
				.findFirstByUser_UserIdOrderByDecidedAtDescDecisionIdDesc(user.getUserId()).orElse(null);
		return UserDetailResponse.from(UserResponse.from(user, credit), assessment, policy);
	}
}
