package com.bts.fairticket.common.auth;

import java.util.Set;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;

public record AdminScope(boolean allServices, Set<String> services) {
	public AdminScope {
		services = Set.copyOf(services);
	}

	/** null denotes unrestricted scope and is only produced by an explicit global credential. */
	public Set<String> restrict(String serviceId) {
		if (serviceId == null) return allServices ? null : services;
		if (!allServices && !services.contains(serviceId)) throw new ApiException(ResponseCode.FORBIDDEN);
		return Set.of(serviceId);
	}
}
