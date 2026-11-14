package com.bts.fairticket.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

// 실행할 때 생성한 테스트 전용 키만 사용한다.
public final class ApiKeyTestSupport {

	private ApiKeyTestSupport() {
	}

	public static String randomKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(key.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static String configuration(Map<String, String> keys) {
		return keys.entrySet().stream()
				.map(entry -> "\"" + entry.getKey() + "\":\"" + hash(entry.getValue()) + "\"")
				.collect(Collectors.joining(",", "{", "}"));
	}
}
