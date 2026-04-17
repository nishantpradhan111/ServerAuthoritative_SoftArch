import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const THINK_TIME_MS = Number(__ENV.THINK_TIME_MS || '200');
const START_VUS = Number(__ENV.START_VUS || '1');
const STAGE1_DURATION = __ENV.STAGE1_DURATION || '30s';
const STAGE1_TARGET = Number(__ENV.STAGE1_TARGET || '5');
const STAGE2_DURATION = __ENV.STAGE2_DURATION || '60s';
const STAGE2_TARGET = Number(__ENV.STAGE2_TARGET || '10');
const STAGE3_DURATION = __ENV.STAGE3_DURATION || '30s';
const STAGE3_TARGET = Number(__ENV.STAGE3_TARGET || '0');
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';

export const errorRate = new Rate('benchmark_errors');

export const options = {
  scenarios: {
    room_lifecycle: {
      executor: 'ramping-vus',
      startVUs: START_VUS,
      stages: [
        { duration: STAGE1_DURATION, target: STAGE1_TARGET },
        { duration: STAGE2_DURATION, target: STAGE2_TARGET },
        { duration: STAGE3_DURATION, target: STAGE3_TARGET },
      ],
      gracefulRampDown: RAMP_DOWN,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
    benchmark_errors: ['rate<0.02'],
  },
};

function jsonHeaders(extra = {}) {
  return {
    'Content-Type': 'application/json',
    ...extra,
  };
}

function failIf(condition, message) {
  if (condition) {
    errorRate.add(1);
    console.error(message);
    return true;
  }
  return false;
}

export default function () {
  errorRate.add(0);

  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const username = `bench${__VU}${__ITER}${Date.now()}`.slice(0, 20);
  const email = `bench-${unique}@example.com`;
  const password = 'BenchmarkPass123!';

  const registerPayload = JSON.stringify({ username, email, password });
  const registerRes = http.post(
    `${BASE_URL}/api/auth/register`,
    registerPayload,
    { headers: jsonHeaders() }
  );

  check(registerRes, {
    'register status is 201': (r) => r.status === 201,
    'register has token': (r) => {
      try {
        return !!r.json('accessToken');
      } catch (_err) {
        return false;
      }
    },
  }) || errorRate.add(1);

  if (failIf(registerRes.status !== 201, `Register failed: ${registerRes.status} ${registerRes.body}`)) {
    sleep(1);
    return;
  }

  const accessToken = registerRes.json('accessToken');
  if (failIf(!accessToken, 'Missing access token from register response')) {
    sleep(1);
    return;
  }

  const createRes = http.post(
    `${BASE_URL}/api/rooms`,
    null,
    {
      headers: jsonHeaders({ Authorization: `Bearer ${accessToken}` }),
    }
  );

  check(createRes, {
    'create room status is 200': (r) => r.status === 200,
    'create room has roomCode': (r) => {
      try {
        return !!r.json('roomCode');
      } catch (_err) {
        return false;
      }
    },
    'create room has token': (r) => {
      try {
        return !!r.json('token');
      } catch (_err) {
        return false;
      }
    },
  }) || errorRate.add(1);

  if (failIf(createRes.status !== 200, `Create room failed: ${createRes.status} ${createRes.body}`)) {
    sleep(1);
    return;
  }

  const roomCode = createRes.json('roomCode');
  const playerToken = createRes.json('token');
  if (failIf(!roomCode || !playerToken, 'Missing roomCode/token from create room response')) {
    sleep(1);
    return;
  }

  sleep(THINK_TIME_MS / 1000);

  const snapshotRes = http.get(
    `${BASE_URL}/api/rooms/${roomCode}`,
    {
      headers: jsonHeaders({
        Authorization: `Bearer ${accessToken}`,
        'X-Player-Token': playerToken,
      }),
    }
  );

  check(snapshotRes, {
    'snapshot status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  if (failIf(snapshotRes.status !== 200, `Snapshot failed: ${snapshotRes.status} ${snapshotRes.body}`)) {
    sleep(1);
    return;
  }

  sleep(THINK_TIME_MS / 1000);

  const leaveRes = http.post(
    `${BASE_URL}/api/rooms/${roomCode}/leave`,
    null,
    {
      headers: jsonHeaders({
        Authorization: `Bearer ${accessToken}`,
        'X-Player-Token': playerToken,
      }),
    }
  );

  check(leaveRes, {
    'leave status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  if (failIf(leaveRes.status !== 200, `Leave failed: ${leaveRes.status} ${leaveRes.body}`)) {
    sleep(1);
  }
}
