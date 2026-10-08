import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_rate: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 1000),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 1000),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(99)<2'],
  },
};

const body = JSON.stringify({
  principal: { id: 'payment-agent', type: 'AI_AGENT' },
  action: { name: 'payment.transfer' },
  resource: { type: 'bank_account', id: 'ACC-1001' },
  context: { amount: 1000 }
});

export default function () {
  const r = http.post((__ENV.DP_URL || 'http://localhost:8091') + '/v1/fast/evaluate', body, { headers: { 'Content-Type': 'application/json' } });
  check(r, { 'HTTP 200': x => x.status === 200, 'not deferred': x => JSON.parse(x.body).decision !== 'DEFER' });
}
