import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

const baseUrl = (__ENV.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const apiKey = __ENV.API_KEY;
const serviceId = __ENV.SERVICE_ID;
const userIds = (__ENV.USER_IDS || '').split(',').map((id) => id.trim()).filter(Boolean);
// Supply a fresh UUID for every run, including simultaneous k6 processes.
const runId = __ENV.RUN_ID;
const duration = Number(__ENV.DURATION_SECONDS || 60);
const levels = [10, 50, 100];
const attempts = new Counter('event_attempts');
const created = new Counter('events_created');
const successRate = new Rate('event_success_rate');
const errorRate = new Rate('event_error_rate');

if (!/^https?:\/\/[^/?#@]+(?::\d+)?$/.test(baseUrl)) {
	throw new Error('API_BASE_URL must be an HTTP(S) origin without credentials or a path');
}
if (!apiKey || !apiKey.trim() || /[\r\n]/.test(apiKey)) {
	throw new Error('API_KEY is required and must not contain newlines');
}
if (!serviceId || !serviceId.trim() || serviceId.length > 50) {
	throw new Error('SERVICE_ID is required (maximum 50 characters)');
}
if (!userIds.length || userIds.some((id) => id.length > 50)) {
	throw new Error('USER_IDS must contain existing internal user IDs for SERVICE_ID');
}
if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(runId || '')) {
	throw new Error('RUN_ID must be a fresh UUID for each run');
}
if (!Number.isSafeInteger(duration) || duration < 1 || duration > 3600) {
	throw new Error('DURATION_SECONDS must be an integer from 1 to 3600');
}

const scenarios = {};
const thresholds = {};
levels.forEach((vus, index) => {
	const scenario = `vus_${vus}`;
	scenarios[scenario] = {
		executor: 'constant-vus',
		vus,
		duration: `${duration}s`,
		startTime: `${index * (duration + 15)}s`,
		gracefulStop: '10s',
	};
	// HTTP success means newly created (200 replays are unexpected in this test).
	thresholds[`event_success_rate{scenario:${scenario}}`] = ['rate>=0.99'];
	thresholds[`event_error_rate{scenario:${scenario}}`] = ['rate<=0.01'];
	thresholds[`http_req_failed{scenario:${scenario}}`] = ['rate<=0.01'];
	// Materialize per-scenario latency/counters in the summary without inventing an SLA.
	thresholds[`http_req_duration{scenario:${scenario}}`] = ['p(95)>=0'];
	thresholds[`event_attempts{scenario:${scenario}}`] = ['count>0'];
	thresholds[`events_created{scenario:${scenario}}`] = ['count>0'];
});

export const options = {
	scenarios,
	thresholds,
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)'],
};

export default function () {
	const externalEventId = `k6-${runId}-${exec.scenario.name}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
	// Current DTO accepts a Seoul local timestamp, without offset or fractional seconds.
	const occurredAt = new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 19);
	const payload = {
		serviceId,
		externalEventId,
		userId: userIds[(exec.vu.idInTest - 1) % userIds.length],
		eventType: 'BOOKING',
		bookingId: externalEventId,
		occurredAt,
		metadata: { bookingDurationSeconds: 30, deviceChanged: false },
	};
	attempts.add(1);
	const response = http.post(`${baseUrl}/api/v1/events`, JSON.stringify(payload), {
		headers: { 'Content-Type': 'application/json', 'X-API-Key': apiKey },
		timeout: '10s',
		redirects: 0,
		responseCallback: http.expectedStatuses(201),
	});
	let body;
	try {
		body = response.json();
	} catch (_) {
		body = null;
	}
	const success = check(response, {
		'new event persisted with matching identity': (r) => r.status === 201
			&& body !== null && body.success === true && body.code === 'COMMON_201'
			&& body.data != null && Number.isInteger(body.data.eventId) && body.data.eventId > 0
			&& body.data.serviceId === serviceId && body.data.externalEventId === externalEventId
			&& body.data.userId === payload.userId && body.data.eventType === payload.eventType,
	});
	created.add(success ? 1 : 0);
	successRate.add(success);
	errorRate.add(!success);
}

export function handleSummary(data) {
	const value = (metric, scenario, field) => {
		const entry = data.metrics[`${metric}{scenario:${scenario}}`];
		return entry ? entry.values[field] : null;
	};
	const format = (number, percent = false) => number == null ? 'N/A'
		: `${(number * (percent ? 100 : 1)).toFixed(2)}${percent ? '%' : ''}`;
	const rows = levels.map((vus) => {
		const scenario = `vus_${vus}`;
		return `| ${vus} | ${value('event_attempts', scenario, 'count') ?? 0} | ${value('events_created', scenario, 'count') ?? 0} | ${format(value('event_success_rate', scenario, 'rate'), true)} | ${format(value('http_req_duration', scenario, 'avg'))} | ${format(value('http_req_duration', scenario, 'p(95)'))} | ${format(value('event_error_rate', scenario, 'rate'), true)} | ${format(value('http_req_failed', scenario, 'rate'), true)} |`;
	});
	const passed = Object.values(data.metrics).every((metric) =>
		Object.values(metric.thresholds || {}).every((threshold) => threshold.ok));
	const report = `# Event API k6 결과\n\nRUN_ID: ${runId}\n\nSERVICE_ID: ${serviceId}\n\n구간별 요청 발생 시간: ${duration}s, threshold: ${passed ? 'PASS' : 'FAIL'}\n\n`
		+ '| VU | 요청 수 | 생성 확인 수 | 성공률 | 평균(ms) | p95(ms) | 오류율 | HTTP 실패율 |\n'
		+ '|---|---|---|---|---|---|---|---|\n' + rows.join('\n')
		+ '\n\nDB 전후 건수 / 실행별 적재 건수 / 중복 검사 결과: 실행 후 기록 필요\n';
	return {
		stdout: report,
		'load-tests/results.md': report,
		'load-tests/results.json': JSON.stringify(data, null, 2),
	};
}
