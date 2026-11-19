import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 20 },  // Ramp up to 20 users
    { duration: '30s', target: 50 },  // Steady load at 50 users
    { duration: '10s', target: 100 }, // Spike to 100 users
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<600'], // 95% of requests under 300ms
    http_req_failed: ['rate<0.01'],                 // Less than 1% errors
  },
};

const BASE_URL = __ENV.EVENTFLOW_URL || 'http://localhost:8085';

export function setup() {
  // Obtain JWT token from backend
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: 'admin@acme.com',
    password: 'SuperSecretPassword123',
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(loginRes, {
    'auth succeeded': (r) => r.status === 200,
  });

  const token = loginRes.json('token');
  return { token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
    'Idempotency-Key': `k6-idem-${__VU}-${__ITER}`,
  };

  const payload = JSON.stringify({
    eventType: 'order.created',
    payload: {
      orderId: `ord_k6_${__VU}_${__ITER}`,
      amount: Math.round((Math.random() * 500 + 10) * 100) / 100,
      customer: `LoadTest User ${__VU}`,
      timestamp: Date.now(),
    },
  });

  // 1. Ingest Event
  const res = http.post(`${BASE_URL}/api/v1/events`, payload, { headers });

  check(res, {
    'event accepted (202)': (r) => r.status === 202,
    'status is ACCEPTED or DUPLICATE': (r) => {
      const status = r.json('status');
      return status === 'ACCEPTED' || status === 'DUPLICATE_ACCEPTED';
    },
  });

  // Small pause between requests
  sleep(0.1);
}
