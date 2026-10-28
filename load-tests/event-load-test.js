import { duration, groupThresholds, sendEvent, summarize, summaryTrendStats } from './event-common.js';

const levels = [10, 50, 100];
const groups = levels.map(vus => ({ name: `vus_${vus}`, seconds: duration }));
const scenarios = {};
levels.forEach((vus, index) => {
    scenarios[`vus_${vus}`] = {
        executor: 'constant-vus', vus, duration: `${duration}s`,
        startTime: `${index * (duration + 15)}s`, gracefulStop: '10s',
    };
});
export const options = { scenarios, thresholds: groupThresholds(groups), summaryTrendStats };
export default function () { sendEvent(); }
export function handleSummary(data) { return summarize(data, 'load', groups); }
