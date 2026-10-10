package com.bts.fairticket.behavior.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.service.EventRegistrationTransaction.RegistrationResult;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;

@Service
public class EventService {
	private final EventRegistrationTransaction transaction;

	public EventService(EventRegistrationTransaction transaction) {
		this.transaction = transaction;
	}

	public RegistrationResult collect(EventRequest request, String authenticatedService) {
		if (!authenticatedService.equals(request.serviceId())) {
			throw new ApiException(ResponseCode.FORBIDDEN);
		}
		try {
			return transaction.register(request);
		} catch (DataIntegrityViolationException exception) {
			return transaction.findExisting(request)
					.orElseThrow(() -> new ApiException(ResponseCode.CONFLICT));
		}
	}
}
