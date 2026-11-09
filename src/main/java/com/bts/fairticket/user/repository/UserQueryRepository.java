package com.bts.fairticket.user.repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import com.bts.fairticket.common.query.QueryPage;
import com.bts.fairticket.common.query.ServiceScopeQuery;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.dto.UserQuery;
import com.bts.fairticket.user.dto.UserResponse;

@Repository
public class UserQueryRepository {
	private final EntityManager em;
	public UserQueryRepository(EntityManager em) { this.em = em; }

	public PageResponse<UserResponse> find(UserQuery filter, Set<String> services) {
		var page = QueryPage.parse(filter.page(), filter.size(), filter.sort(), Map.of(
				"createdAt", "u.createdAt", "userId", "u.userId", "creditScore", "c.creditScore"),
				"createdAt", "u.userId");
		if (filter.minCreditScore() != null && filter.maxCreditScore() != null
				&& filter.minCreditScore() > filter.maxCreditScore()) QueryPage.invalid();
		if (filter.createdFrom() != null && filter.createdTo() != null
				&& !filter.createdFrom().isBefore(filter.createdTo())) QueryPage.invalid();
		for (var date : new java.time.LocalDateTime[]{filter.createdFrom(), filter.createdTo()}) {
			if (date != null && (date.getYear() < 1000 || date.getYear() > 9999)) QueryPage.invalid();
		}
		StringBuilder where = new StringBuilder(" where 1=1");
		Map<String, Object> parameters = new LinkedHashMap<>();
		if (services != null) {
			if (services.isEmpty()) return PageResponse.of(java.util.List.of(), page.page(), page.size(), 0);
			where.append(" and ").append(ServiceScopeQuery.predicate("u.serviceId"));
		}
		add(where, parameters, "userId", "u.userId =", filter.userId());
		add(where, parameters, "externalUserId", "u.externalUserId =", filter.externalUserId());
		add(where, parameters, "accountStatus", "u.accountStatus =", filter.accountStatus());
		add(where, parameters, "tier", "c.tier =", filter.tier());
		add(where, parameters, "minCreditScore", "c.creditScore >=", filter.minCreditScore());
		add(where, parameters, "maxCreditScore", "c.creditScore <=", filter.maxCreditScore());
		add(where, parameters, "createdFrom", "u.createdAt >=", filter.createdFrom());
		add(where, parameters, "createdTo", "u.createdAt <", filter.createdTo());
		String from = " from UserAccount u left join UserCredit c on c.userId = u.userId" + where;
		var count = bind(em.createQuery("select count(u)" + from, Long.class), parameters);
		if (services != null) ServiceScopeQuery.bind(count, services);
		long total = count.getSingleResult();
		var query = em.createQuery("select new com.bts.fairticket.user.dto.UserResponse("
				+ "u.userId, u.serviceId, u.externalUserId, u.accountStatus, u.createdAt, c.creditScore, c.tier)"
				+ from + " order by " + page.orderBy(), UserResponse.class);
		bind(query, parameters);
		if (services != null) ServiceScopeQuery.bind(query, services);
		var content = query.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList();
		if (content.stream().anyMatch(user -> user.creditScore() == null)) {
			throw new ApiException(ResponseCode.INTERNAL_SERVER_ERROR);
		}
		return PageResponse.of(content, page.page(), page.size(), total);
	}

	private void add(StringBuilder where, Map<String, Object> parameters, String name, String condition, Object value) {
		if (value != null) { where.append(" and ").append(condition).append(" :").append(name); parameters.put(name, value); }
	}
	private <T extends Query> T bind(T query, Map<String, Object> parameters) {
		parameters.forEach(query::setParameter); return query;
	}
}
