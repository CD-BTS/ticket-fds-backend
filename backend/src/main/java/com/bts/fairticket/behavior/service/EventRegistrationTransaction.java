package com.bts.fairticket.behavior.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bts.fairticket.behavior.dto.EventRequest;
import com.bts.fairticket.behavior.dto.EventResponse;
import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.behavior.repository.BehaviorEventRepository;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;
import com.bts.fairticket.user.repository.UserAccountRepository;

@Service
public class EventRegistrationTransaction {
	private final BehaviorEventRepository events;
	private final UserAccountRepository users;

	public EventRegistrationTransaction(BehaviorEventRepository events, UserAccountRepository users) {
		this.events = events;
		this.users = users;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public RegistrationResult register(EventRequest request) {
		var user = users.findById(request.userId())
				.filter(account -> account.getServiceId().equals(request.serviceId()))
				.orElseThrow(() -> new ApiException(ResponseCode.USER_NOT_FOUND));
		var existing = findExisting(request);
		if (existing.isPresent()) {
			return existing.get();
		}
		var saved = events.saveAndFlush(new BehaviorEvent(user, request.serviceId(), request.externalEventId(),
				request.eventType().name(), request.bookingId(), request.ticketId(),
				request.occurrenceTime(), request.metadata()));
		return new RegistrationResult(EventResponse.from(saved), true);
	}

	// UNIQUE 충돌이 롤백된 후 새로운 트랜잭션에서 커밋된 이벤트를 비교한다.
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, readOnly = true)
	public Optional<RegistrationResult> findExisting(EventRequest request) {
		return events.findByServiceIdAndExternalEventId(request.serviceId(), request.externalEventId())
				.map(event -> {
					if (!event.getServiceId().equals(request.serviceId())
							|| !referencesSameUser(event, request)
							|| !event.getEventType().equals(request.eventType().name())
							|| !Objects.equals(event.getBookingId(), request.bookingId())
							|| !Objects.equals(event.getTicketId(), request.ticketId())
							|| !event.getOccurredAt().equals(request.occurrenceTime())
							|| !sameJson(event.getMetadata(), request.metadata())) {
						throw new ApiException(ResponseCode.DATA_CONFLICT);
					}
					return new RegistrationResult(EventResponse.from(event), false);
				});
	}

	private boolean referencesSameUser(BehaviorEvent event, EventRequest request) {
		String storedId = event.getUser().getUserId();
		if (storedId.equals(request.userId())) {
			return true;
		}
		// DB 문자열 비교로 같은 PK가 조회된 경우 실제 저장된 사용자 ID로 비교한다.
		return users.findById(request.userId())
				.filter(user -> user.getServiceId().equals(request.serviceId()))
				.map(user -> storedId.equals(user.getUserId())).orElse(false);
	}

	// JSON 객체 순서와 DB 역직렬화 시 숫자 Java 타입의 차이는 의미상 변경으로 취급하지 않는다.
	private static boolean sameJson(Object left, Object right) {
		if (left instanceof Number a && right instanceof Number b) {
			return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
		}
		if (left instanceof Map<?, ?> a && right instanceof Map<?, ?> b) {
			return a.keySet().equals(b.keySet())
					&& a.keySet().stream().allMatch(key -> sameJson(a.get(key), b.get(key)));
		}
		if (left instanceof List<?> a && right instanceof List<?> b) {
			if (a.size() != b.size()) {
				return false;
			}
			for (int index = 0; index < a.size(); index++) {
				if (!sameJson(a.get(index), b.get(index))) {
					return false;
				}
			}
			return true;
		}
		return Objects.equals(left, right);
	}

	public record RegistrationResult(EventResponse event, boolean created) { }
}
