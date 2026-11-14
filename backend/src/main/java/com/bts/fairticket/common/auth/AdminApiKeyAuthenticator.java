package com.bts.fairticket.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Separate administrator credentials; no role is inferred from a service name. */
public class AdminApiKeyAuthenticator {

	public record Credential(String hash, boolean allServices, Set<String> services) { }
	private final List<Credential> credentials;

	public AdminApiKeyAuthenticator(String configuration, String serviceConfiguration) {
		try {
			var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
			List<Credential> parsed = mapper.readValue(configuration, new TypeReference<List<Credential>>() { });
			Map<String, String> serviceHashes = mapper.readValue(serviceConfiguration,
					new TypeReference<Map<String, String>>() { });
			Set<String> hashes = new HashSet<>();
			serviceHashes.values().forEach(hash -> hashes.add(hash.toLowerCase(java.util.Locale.ROOT)));
			if (parsed == null) throw new IllegalArgumentException();
			for (Credential credential : parsed) {
				if (credential == null || credential.hash() == null
						|| !credential.hash().matches("[0-9a-fA-F]{64}")
						|| !hashes.add(credential.hash().toLowerCase(java.util.Locale.ROOT))
						|| credential.services() == null
						|| (credential.allServices() ? !credential.services().isEmpty() : credential.services().isEmpty())
						|| credential.services().stream().anyMatch(service -> service == null || service.isBlank()
								|| service.length() > 50 || !service.equals(service.strip()))) {
					throw new IllegalArgumentException();
				}
			}
			credentials = List.copyOf(parsed);
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Invalid administrator API Key configuration.");
		}
	}

	public AdminScope authenticate(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) return null;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
			AdminScope scope = null;
			for (Credential credential : credentials) {
				if (MessageDigest.isEqual(hash, java.util.HexFormat.of().parseHex(credential.hash()))) {
					scope = new AdminScope(credential.allServices(), Set.copyOf(credential.services()));
				}
			}
			return scope;
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable.");
		}
	}
}
