package com.bts.fairticket.common.auth;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.ApiKeyTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyAuthenticatorTests {

	@Test
	void authenticatesConfiguredServiceAndRejectsInvalidKeys() {
		String key = ApiKeyTestSupport.randomKey();
		var authenticator = new ApiKeyAuthenticator(ApiKeyTestSupport.configuration(Map.of("service-a", key)));
		assertEquals("service-a", authenticator.authenticate(key));
		for (String invalid : new String[] {null, "", " ", ApiKeyTestSupport.randomKey()}) {
			ApiException exception = assertThrows(ApiException.class, () -> authenticator.authenticate(invalid));
			assertEquals(ResponseCode.UNAUTHORIZED, exception.getResponseCode());
		}
	}

	@Test
	void emptyConfigurationRejectsAllRequests() {
		var authenticator = new ApiKeyAuthenticator("{}");
		assertThrows(ApiException.class, () -> authenticator.authenticate(ApiKeyTestSupport.randomKey()));
	}

	@Test
	void rejectsAmbiguousAndMalformedConfigurationWithoutExposingSecrets() {
		String key = ApiKeyTestSupport.randomKey();
		String duplicate = ApiKeyTestSupport.configuration(Map.of("service-a", key, "service-b", key));
		assertThrows(IllegalStateException.class, () -> new ApiKeyAuthenticator(duplicate));
		String rawConfiguration = "{\"service-a\":\"" + key + "\"}";
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> new ApiKeyAuthenticator(rawConfiguration));
		assertFalse(exception.getMessage().contains(key));
		for (String invalid : new String[] {"null", "[]", "{", "{\"\":\"" + ApiKeyTestSupport.hash(key) + "\"}"}) {
			assertThrows(IllegalStateException.class, () -> new ApiKeyAuthenticator(invalid));
		}
	}

	@Test
	void rejectsDuplicateServiceNames() {
		String hash = ApiKeyTestSupport.hash(ApiKeyTestSupport.randomKey());
		assertThrows(IllegalStateException.class,
				() -> new ApiKeyAuthenticator("{\"a\":\"" + hash + "\",\"a\":\"" + hash + "\"}"));
	}
}
