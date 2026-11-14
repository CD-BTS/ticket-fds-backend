package com.bts.fairticket.common.auth;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.user.ApiKeyTestSupport;
import static org.junit.jupiter.api.Assertions.*;

class AdminApiKeyAuthenticatorTests {
	private final String key = ApiKeyTestSupport.randomKey();
	private String config(boolean all, String services) {
		return "[{\"hash\":\"" + ApiKeyTestSupport.hash(key) + "\",\"allServices\":" + all
				+ ",\"services\":" + services + "}]";
	}

	@Test
	void scopesAreExplicitAndCannotBeExpandedByRequests() {
		var auth = new AdminApiKeyAuthenticator(config(false, "[\"a\",\"b\"]"), "{}");
		var scope = auth.authenticate(key);
		assertEquals(Set.of("a", "b"), scope.restrict(null));
		assertEquals(Set.of("a"), scope.restrict("a"));
		assertThrows(ApiException.class, () -> scope.restrict("A"));
		assertThrows(ApiException.class, () -> scope.restrict("c"));
		assertNull(auth.authenticate("incorrect"));
		assertNull(auth.authenticate(null));
		assertNull(new AdminApiKeyAuthenticator("[]", "{}").authenticate(key));
		var global = new AdminApiKeyAuthenticator(config(true, "[]"), "{}").authenticate(key);
		assertNull(global.restrict(null));
		assertEquals(Set.of("any-service"), global.restrict("any-service"));
	}

	@Test
	void rejectsAmbiguousMalformedAndPrivilegeEscalatingConfigurationWithoutSecrets() {
		String valid = config(false, "[\"a\"]");
		String duplicate = valid.substring(0, valid.length() - 1) + "," + valid.substring(1);
		for (String value : new String[]{"null", "{}", "broken", config(false, "[]"),
				config(true, "[\"a\"]"), config(false, "[\" \"]"), config(false, "null"), duplicate}) {
			var error = assertThrows(IllegalStateException.class, () -> new AdminApiKeyAuthenticator(value, "{}"));
			assertFalse(error.getMessage().contains(key));
			assertNull(error.getCause());
		}
		assertThrows(IllegalStateException.class, () -> new AdminApiKeyAuthenticator(valid,
				ApiKeyTestSupport.configuration(Map.of("a", key))));
	}
}
