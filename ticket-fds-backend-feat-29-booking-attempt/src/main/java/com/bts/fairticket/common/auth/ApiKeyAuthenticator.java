package com.bts.fairticket.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;

public class ApiKeyAuthenticator {

	private final Map<String, byte[]> serviceHashes;

	public ApiKeyAuthenticator(String configuration) {
		Map<String, String> configured;
		try {
			configured = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
					.readValue(configuration, new TypeReference<Map<String, String>>() { });
		} catch (RuntimeException exception) {
			// 파싱 예외에는 원본 설정이 포함될 수 있으므로 메시지와 원인을 전달하지 않는다.
			throw new IllegalStateException("API Key 해시 설정은 서비스 ID와 SHA-256 해시의 JSON 객체여야 합니다.");
		}
		if (configured == null) {
			throw new IllegalStateException("API Key 해시 설정은 JSON 객체여야 합니다.");
		}
		Map<String, byte[]> hashes = new LinkedHashMap<>();
		Set<String> uniqueHashes = new HashSet<>();
		configured.forEach((serviceId, hash) -> {
			if (serviceId == null || serviceId.isBlank() || serviceId.length() > 50
					|| !serviceId.equals(serviceId.strip())
					|| hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
				throw new IllegalStateException("API Key 해시 설정의 서비스 ID 또는 SHA-256 형식이 잘못되었습니다.");
			}
			String normalized = hash.toLowerCase(java.util.Locale.ROOT);
			if (!uniqueHashes.add(normalized)) {
				throw new IllegalStateException("서로 다른 서비스는 동일한 API Key를 사용할 수 없습니다.");
			}
			hashes.put(serviceId, HexFormat.of().parseHex(normalized));
		});
		serviceHashes = Map.copyOf(hashes);
	}

	public String authenticate(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new ApiException(ResponseCode.UNAUTHORIZED);
		}
		byte[] presented = hash(apiKey);
		String authenticatedService = null;
		for (var entry : serviceHashes.entrySet()) {
			if (MessageDigest.isEqual(entry.getValue(), presented)) {
				authenticatedService = entry.getKey();
			}
		}
		if (authenticatedService == null) {
			throw new ApiException(ResponseCode.UNAUTHORIZED);
		}
		return authenticatedService;
	}

	private static byte[] hash(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.");
		}
	}
}
