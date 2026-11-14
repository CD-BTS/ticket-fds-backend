package com.bts.fairticket.behavior.repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import com.bts.fairticket.behavior.dto.EventQuery;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.query.QueryPage;
import com.bts.fairticket.common.response.PageResponse;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.repository.UserAccountRepository;

@Repository
public class UserEventQueryRepository {
	private final EntityManager em;
	private final UserAccountRepository users;
	public UserEventQueryRepository(EntityManager em, UserAccountRepository users) { this.em = em; this.users = users; }

	public PageResponse<EventResponse> find(String userId, String serviceId, EventQuery filter) {
		var page = QueryPage.parse(filter.page(), filter.size(), filter.sort(),
				Map.of("occurredAt", "e.occurredAt", "eventId", "e.eventId"), "occurredAt", "e.eventId");
		LocalDateTime from = utc(filter.from()), to = utc(filter.to());
		if (from != null && to != null && !from.isBefore(to)) QueryPage.invalid();
		var user = users.findById(userId).filter(account -> account.getServiceId().equals(serviceId))
				.orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));
		String where = " where e.user.userId = :userId and e.serviceId = :serviceId"
				+ (filter.eventType() == null ? "" : " and e.eventType = :eventType")
				+ (from == null ? "" : " and e.occurredAt >= :from")
				+ (to == null ? "" : " and e.occurredAt < :to");
		var count = em.createQuery("select count(e) from BehaviorEvent e" + where, Long.class);
		var rows = em.createQuery("select e from BehaviorEvent e" + where + " order by " + page.orderBy(), BehaviorEvent.class);
		for (var query : java.util.List.of(count, rows)) {
			query.setParameter("userId", user.getUserId()); query.setParameter("serviceId", serviceId);
			if (filter.eventType() != null) query.setParameter("eventType", filter.eventType());
			if (from != null) query.setParameter("from", from);
			if (to != null) query.setParameter("to", to);
		}
		long total = count.getSingleResult();
		var content = rows.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList()
				.stream().map(EventResponse::from).toList();
		return PageResponse.of(content, page.page(), page.size(), total);
	}

	private LocalDateTime utc(OffsetDateTime time) {
		if (time == null) return null;
		var utc = time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
		if (utc.getYear() < 1000 || utc.getYear() > 9999 || utc.getNano() != 0) QueryPage.invalid();
		return utc;
	}
}
