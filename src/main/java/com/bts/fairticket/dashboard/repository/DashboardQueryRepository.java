package com.bts.fairticket.dashboard.repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import com.bts.fairticket.dashboard.dto.DashboardSummary;
import com.bts.fairticket.common.query.ServiceScopeQuery;

@Repository
public class DashboardQueryRepository {
	private final EntityManager em;
	public DashboardQueryRepository(EntityManager em) { this.em = em; }

	public DashboardSummary summarize(Set<String> services) {
		Map<String, Long> states = group("u.accountStatus", "UserAccount u", "u.serviceId", services);
		Map<String, Long> tiers = group("c.tier", "UserCredit c", "c.user.serviceId", services);
		Map<String, Long> events = new LinkedHashMap<>(Map.of("BOOKING", 0L, "CANCEL", 0L, "CHECK_IN", 0L));
		events.putAll(group("e.eventType", "BehaviorEvent e", "e.serviceId", services));
		String where = services == null ? "" : " where " + ServiceScopeQuery.predicate("c.user.serviceId");
		Double average = null;
		if (services == null || !services.isEmpty()) {
			var query = em.createQuery("select avg(c.creditScore) from UserCredit c" + where, Double.class);
			if (services != null) ServiceScopeQuery.bind(query, services);
			average = query.getSingleResult();
		}
		return new DashboardSummary(sum(states), states.getOrDefault("ACTIVE", 0L), states.getOrDefault("SUSPENDED", 0L),
				sum(tiers), average, tiers, sum(events), events);
	}

	private Map<String, Long> group(String field, String from, String serviceField, Set<String> services) {
		Map<String, Long> result = new LinkedHashMap<>();
		if (services != null && services.isEmpty()) return result;
		var query = em.createQuery("select " + field + ", count(*) from " + from
				+ (services == null ? "" : " where " + ServiceScopeQuery.predicate(serviceField))
				+ " group by " + field + " order by " + field, Object[].class);
		if (services != null) ServiceScopeQuery.bind(query, services);
		query.getResultList().forEach(row -> result.put((String) row[0], (Long) row[1]));
		return result;
	}
	private long sum(Map<String, Long> values) { return values.values().stream().mapToLong(Long::longValue).sum(); }
}
