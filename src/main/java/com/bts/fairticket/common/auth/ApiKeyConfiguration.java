package com.bts.fairticket.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class ApiKeyConfiguration {

	@Bean
	ApiKeyAuthenticator apiKeyAuthenticator(@Value("${BTS_API_KEY_HASHES:{}}") String hashes) {
		return new ApiKeyAuthenticator(hashes);
	}

	@Bean
	ApiKeyFilter apiKeyFilter(ApiKeyAuthenticator authenticator, JsonMapper jsonMapper,
			@Value("${BTS_ADMIN_API_KEYS:[]}") String adminKeys,
			@Value("${BTS_API_KEY_HASHES:{}}") String serviceKeys) {
		return new ApiKeyFilter(authenticator, new AdminApiKeyAuthenticator(adminKeys, serviceKeys), jsonMapper);
	}
}
