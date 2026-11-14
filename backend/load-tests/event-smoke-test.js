import { sendEvent } from './event-common.js';

export const options = {
    scenarios: {
        smoke: {
            executor: 'constant-vus',
            vus: 5,
            duration: '5s',
        },
    },
};

export default function () {
    sendEvent();
}
