import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

const baseUrl = (__ENV.API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const apiKey = __ENV.API_KEY;
const serviceId = __ENV.SERVICE_ID;
const userIds = (__ENV.USER_IDS || '').split(',').map((id) => id.trim()).filter(Boolean);
// Supply a fresh UUID for every run, including simultaneous k6 processes.
export const runId = __ENV.RUN_ID;
export const duration = Number(__ENV.DURATION_SECONDS || 60);
const attempts = new Counter('event_attempts');
const created = new Counter('events_created');
const successRate = new Rate('event_success_rate');
const errorRate = new Rate('event_error_rate');
const failureCount = new Counter('event_failure_count');
const networkFailureCount = new Counter('event_network_failure_count');
const httpFailureCount = new Counter('event_http_failure_count');

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


if (!/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(baseUrl)) {
	throw new Error('Only loopback API_BASE_URL is allowed; verify the server DB_URL points to local test MySQL');
}

export function groupThresholds(groups) {
    const thresholds = {};
    groups.forEach(({ name }) => {
        thresholds[`event_success_rate{test_group:${name}}`] = ['rate>=0.99'];
        thresholds[`event_error_rate{test_group:${name}}`] = ['rate<=0.01'];
        thresholds[`http_req_failed{test_group:${name}}`] = ['rate<=0.01'];
        thresholds[`http_req_duration{test_group:${name}}`] = ['p(95)>=0'];
        thresholds[`event_attempts{test_group:${name}}`] = ['count>0'];
        thresholds[`events_created{test_group:${name}}`] = ['count>0'];
    });
    return thresholds;
}

export const summaryTrendStats = ['avg', 'min', 'med', 'max', 'p(95)'];

export function sendEvent(group = exec.scenario.name) {
	const tags = { test_group: group };
	const externalEventId = `k6-${runId}-${group}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
	// Offset-aware, whole-second UTC timestamp accepted by EventRequest.
	const occurredAt = new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
	const payload = {
		serviceId,
		externalEventId,
		userId: userIds[(exec.vu.idInTest - 1) % userIds.length],
		eventType: 'BOOKING',
		bookingId: externalEventId,
		occurredAt,
		metadata: { bookingDurationSeconds: 30, deviceChanged: false },
	};
	attempts.add(1, tags);
	const response = http.post(`${baseUrl}/api/v1/events`, JSON.stringify(payload), {
		headers: { 'Content-Type': 'application/json', 'X-API-Key': apiKey },
		tags,
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
	}, tags);
	created.add(success ? 1 : 0, tags);
successRate.add(success, tags);
errorRate.add(!success, tags);

if (!success) {
    let failureType;

    if (response.status === 0) {
        failureType = 'network_error';
    } else if (response.status !== 201) {
        failureType = `http_${response.status}`;
    } else {
        failureType = 'response_validation_failed';
    }

    failureCount.add(1, {
        ...tags,
        failure_type: failureType,
    });
	if (response.status === 0) {
    networkFailureCount.add(1, tags);
	} else if (response.status !== 201) {
    httpFailureCount.add(1, tags);
	}

    if (exec.vu.iterationInInstance === 0) {
        console.error(
            `[EVENT FAILURE] type=${failureType}, ` +
            `status=${response.status}, ` +
            `error=${response.error || 'none'}, ` +
            `error_code=${response.error_code || 'none'}`
        );
    }
}
    return { success, status: response.status, errorCode: response.error_code || 0,
        durationMs: response.timings?.duration ?? 0 };
}


export function summarize(data, type, groups) {
    const value = (metric, name, field) => data.metrics[`${metric}{test_group:${name}}`]?.values[field];
    const fmt = (n, percent = false) => n == null ? 'N/A' : (n * (percent ? 100 : 1)).toFixed(2) + (percent ? '%' : '');
    const passed = Object.values(data.metrics).every(m => Object.values(m.thresholds || {}).every(t => t.ok));
    const rows = groups.map(({ name, seconds }) => {
        const count = value('event_attempts', name, 'count') || 0;
        const saved = value('events_created', name, 'count') || 0;
        return `| ${name} | ${seconds} | ${count} | ${saved} | ${fmt(count / seconds)} | ${fmt(saved / seconds)} | ${fmt(value('event_success_rate', name, 'rate'), true)} | ${fmt(value('http_req_duration', name, 'avg'))} | ${fmt(value('http_req_duration', name, 'p(95)'))} | ${fmt(value('event_error_rate', name, 'rate'), true)} | ${fmt(value('http_req_failed', name, 'rate'), true)} |`;
    });
    const report = `# Event API ${type} results\n\nRUN_ID: ${runId}\nSERVICE_ID: ${serviceId}\nUsers: ${userIds.length}\nThreshold: ${passed ? 'PASS' : 'FAIL'}\n\n`
        + '| Group | Planned seconds | Attempts | Created | Request RPS | Created/s | Success | Avg(ms) | p95(ms) | Error | HTTP failed |\n'
        + '|---|---|---|---|---|---|---|---|---|---|---|\n' + rows.join('\n')
        + '\n\nRates use counts / planned phase seconds, including graceful completions. Aborted runs do not report actual elapsed-time RPS.\nRecord DB before/after, RUN_ID/group counts and duplicates separately.\n';
    const stem = `load-tests/results-${type}-${runId}`;
    return { stdout: report, [`${stem}.md`]: report,
        [`${stem}.json`]: JSON.stringify({ testType: type, runId, serviceId, userCount: userIds.length, groups, ...data }, null, 2) };
}
