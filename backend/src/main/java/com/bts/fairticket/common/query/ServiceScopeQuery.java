package com.bts.fairticket.common.query;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import jakarta.persistence.Query;

/** MySQL service identifiers may use a case-insensitive collation; authorization must not. */
public final class ServiceScopeQuery {
	private ServiceScopeQuery() { }
	public static String predicate(String field) {
		// Keep the ordinary predicate for index use and additionally require byte-exact identifiers.
		return field + " in :services and function('hex', " + field + ") in :serviceBytes";
	}
	public static void bind(Query query, Set<String> services) {
		query.setParameter("services", services);
		query.setParameter("serviceBytes", services.stream().map(service ->
				HexFormat.of().withUpperCase().formatHex(service.getBytes(StandardCharsets.UTF_8))).toList());
	}
}
