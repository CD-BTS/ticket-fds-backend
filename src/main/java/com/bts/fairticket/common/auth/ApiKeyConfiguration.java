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
	ApiKeyFilter apiKeyFilter(ApiKeyAuthenticator authenticator, JsonMapper jsonMapper) {
		return new ApiKeyFilter(authenticator, jsonMapper);
	}
}
