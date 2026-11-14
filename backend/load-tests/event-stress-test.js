import { duration, groupThresholds, sendEvent, summarize, summaryTrendStats } from './event-common.js';

// One level per process: the PowerShell runner gates progression on the exit code.
const vus = Number(__ENV.STRESS_VUS || 200);
if (![200, 300, 400, 500, 1000].includes(vus)) throw new Error('STRESS_VUS must be 200, 300, 400, 500 or 1000');
const groups = [{ name: `vus_${vus}`, seconds: duration }];
export const options = {
    scenarios: { [`vus_${vus}`]: { executor: 'constant-vus', vus, duration: `${duration}s`, gracefulStop: '10s' } },
    thresholds: groupThresholds(groups), summaryTrendStats,
};
export default function () { sendEvent(); }
export function handleSummary(data) {
    console.log('[DEBUG] handleSummary called');
    return summarize(data, `stress-${vus}`, groups);
}
