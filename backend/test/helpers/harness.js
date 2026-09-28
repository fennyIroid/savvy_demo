'use strict';
const { createApp } = require('../../src/app');
const { buildConfig } = require('../../src/config');

async function startHarness(configOverrides = {}) {
  const clock = { t: Date.parse('2026-09-25T09:00:00Z'), now() { return this.t; } };
  const config = buildConfig(configOverrides);
  const ctx = createApp({ config, clock });
  const server = await new Promise((resolve) => { const s = ctx.app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;

  async function call(method, path, body, token) {
    const res = await fetch(base + path, {
      method,
      headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, body: await res.json() };
  }
  async function device(email, platform = 'ios') {
    const r = await call('POST', '/v1/devices/register', { email, platform });
    return { ...r.body, token: r.body.device_token };
  }
  return {
    ...ctx, clock, call, device,
    advance(minutes) { clock.t += minutes * 60000; },
    close: () => new Promise((r) => server.close(r)),
  };
}
module.exports = { startHarness };
