const { test } = require('node:test');
const assert = require('node:assert/strict');
const policy = require('../functions/message-policy');
const now = 1800000000000;
const input = { locale: 'en', type: 'teaching', title: 'Begin with care', summary: 'A moment for your practice.',
  body: 'Let your next repetition begin with care.', attribution: 'Anushtanam' };

test('content is restricted to supported plain-text fields and bundled artwork', () => {
  const content = policy.content({ ...input, arbitraryScript: 'bad', imageUrl: 'https://unknown.example' });
  assert.equal(content.arbitraryScript, undefined);
  assert.equal(content.imageUrl, undefined);
  assert.equal(content.art, 'river');
  for (const patch of [{ locale: 'unknown' }, { art: 'https://example.com/image' }, { body: '' },
    { title: 'a'.repeat(101) }, { type: 'html' }]) assert.throws(() => policy.content({ ...input, ...patch }));
});
test('expiry prevents stale sends, but there is no daily limit or quiet-hours gate', () => {
  assert.throws(() => policy.window(now, now, now));
  policy.window(now, now + policy.MAX_LIFETIME + 1, now);
  policy.window(now, now + 10000, now);
  const device = { enabled: true, schemaVersion: 1, locale: 'en', token: 'token', updatedAt: { toMillis: () => now } };
  assert.equal(policy.eligible(device, { audience: 'all' }, now, new Set(), 'phone'), true);
  assert.equal(policy.eligible({ ...device, enabled: false }, { audience: 'all' }, now, new Set(), 'phone'), false);
  assert.equal(policy.eligible({ ...device, updatedAt: { toMillis: () => now - 31 * 86400000 } },
    { audience: 'all' }, now, new Set(), 'phone'), false);
});
test('test sends never include an unregistered production phone', () => {
  const device = { enabled: true, schemaVersion: 1, locale: 'en', token: 'token', updatedAt: { toMillis: () => now } };
  assert.equal(policy.eligible(device, { audience: 'test' }, now, new Set(['test-phone']), 'production-phone'), false);
  assert.equal(policy.eligible(device, { audience: 'test' }, now, new Set(['test-phone']), 'test-phone'), true);
});
