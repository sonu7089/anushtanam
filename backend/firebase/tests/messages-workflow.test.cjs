const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const policy = require('../functions/message-policy');

// Exercise the exported handlers without a real provider or production credentials.
function harness() {
  const records = new Map();
  const sends = [];
  let failure = null;
  let nextId = 0;
  class HttpsError extends Error { constructor(code, message) { super(message); this.code = code; } }
  function snapshot(path) {
    return { id: path.split('/').at(-1), exists: records.has(path), ref: reference(path), data: () => records.get(path) };
  }
  function put(path, data, merge = false) {
    const target = merge ? { ...records.get(path) } : {};
    for (const [key, value] of Object.entries(data)) {
      target[key] = value && value.increment !== undefined ? (target[key] || 0) + value.increment : value;
    }
    records.set(path, target);
  }
  function reference(path) {
    return { path, id: path.split('/').at(-1), get: async () => snapshot(path),
      set: async data => put(path, data), update: async data => put(path, data, true),
      delete: async () => records.delete(path), collection: name => query(`${path}/${name}`) };
  }
  function query(path, filters = [], ordering = [], count = Infinity, cursor = null) {
    return { doc: id => reference(`${path}/${id || `auto-${++nextId}`}`),
      where: (key, op, value) => query(path, [...filters, [key, op, value]], ordering, count, cursor),
      orderBy: (key, direction = 'asc') => query(path, filters, [...ordering, [key, direction]], count, cursor),
      limit: n => query(path, filters, ordering, n, cursor),
      startAfter: id => query(path, filters, ordering, count, id),
      get: async () => {
        let docs = [...records.keys()].filter(key => key.startsWith(path + '/') && key.split('/').length === path.split('/').length + 1)
          .map(snapshot).filter(d => filters.every(([key, op, value]) => {
            const field = d.data()[key];
            return op === 'in' ? value.includes(field) : op === '<=' ? field <= value : field === value;
          }));
        for (const [key, dir] of [...ordering].reverse()) docs.sort((a, b) => {
          const av = key === '__name__' ? a.id : a.data()[key];
          const bv = key === '__name__' ? b.id : b.data()[key];
          return (av < bv ? -1 : av > bv ? 1 : 0) * (dir === 'desc' ? -1 : 1);
        });
        if (cursor) docs = docs.filter(d => d.id > cursor);
        docs = docs.slice(0, count);
        return { docs, size: docs.length };
      } };
  }
  const firestore = { collection: query, doc: reference,
    runTransaction: async block => block({ get: ref => ref.get(), set: (ref, data) => put(ref.path, data),
      create: (ref, data) => { assert.equal(records.has(ref.path), false); put(ref.path, data); },
      update: (ref, data) => put(ref.path, data, true), delete: ref => records.delete(ref.path) }) };
  const exported = {};
  vm.runInNewContext(fs.readFileSync(require.resolve('../functions/messages'), 'utf8'), {
    exports: exported, Date, Intl, Set, console,
    require: name => {
      if (name === 'firebase-functions/v2/https') return { onCall: (_, fn) => fn, HttpsError };
      if (name === 'firebase-functions/v2/scheduler') return { onSchedule: (_, fn) => fn };
      if (name === 'firebase-functions/v2/firestore') return { onDocumentDeleted: (_, fn) => fn };
      if (name === 'firebase-admin/firestore') return { getFirestore: () => firestore,
        FieldValue: { increment: n => ({ increment: n }) }, FieldPath: { documentId: () => '__name__' } };
      if (name === 'firebase-admin/messaging') return { getMessaging: () => ({ send: async message => {
        sends.push(message);
        if (failure) throw failure;
        return 'provider-accepted';
      } }) };
      if (name === './message-policy') return policy;
      return require(name);
    }
  });
  const call = (data, role = 'publisher') => exported.adminMessages({ data,
    auth: role ? { uid: 'admin', token: { messageRole: role } } : null });
  return { records, sends, call, tick: exported.tickMessages, fail: value => { failure = value; } };
}
const content = { locale: 'en', type: 'message', title: 'Begin with care', summary: 'Take your time.', body: 'A quiet moment for you.' };
test('the dedicated Home reflection can be revised restored and removed without sending', async () => {
  const h = harness();
  const contentId = 'home-reflection';
  const window = { startsAt: Date.now() - 1000, expiresAt: Date.now() + 31 * 86400000, timezone: 'Asia/Kolkata' };
  await h.call({ op: 'saveDraft', contentId, expectedRevision: 0, content: { ...content, type: 'teaching' } });
  await h.call({ op: 'publish', contentId, revision: 1, ...window });
  await h.call({ op: 'saveDraft', contentId, expectedRevision: 1, content: { ...content, title: 'Begin again today' } });
  assert.equal(h.records.get('publishedContent/home-reflection').revision, 1);
  await h.call({ op: 'publish', contentId, revision: 2, ...window });
  assert.equal(h.records.get('publishedContent/home-reflection').title, 'Begin again today');
  await h.call({ op: 'publish', contentId, revision: 1, ...window });
  assert.equal(h.records.get('publishedContent/home-reflection').title, content.title);
  await h.tick();
  assert.equal(h.sends.length, 0);
  assert.equal([...h.records.keys()].some(key => key.startsWith('campaigns/')), false);
  await h.call({ op: 'unpublish', contentId });
  assert.equal(h.records.has('publishedContent/home-reflection'), false);
});

async function prepare(h) {
  await h.call({ op: 'saveDraft', contentId: 'note', expectedRevision: 0, content });
  const now = Date.now();
  await h.call({ op: 'publish', contentId: 'note', revision: 1, startsAt: now - 1000, expiresAt: now + 60000, timezone: 'Asia/Kolkata' });
  const campaign = { op: 'createCampaign', campaignId: 'send-1', contentId: 'note', revision: 1,
    audience: 'test', sendAt: now, expiresAt: now + 50000, timezone: 'Asia/Kolkata' };
  h.records.set('messageInstallations/test-phone', { token: 'test-token', enabled: true, schemaVersion: 1, locale: 'en', updatedAt: { toMillis: () => now } });
  h.records.set('messageInstallations/general-phone', { token: 'general-token', enabled: true, schemaVersion: 1, locale: 'en', updatedAt: { toMillis: () => now } });
  h.records.set('messageTestDevices/test-phone', { label: 'Test phone' });
  return campaign;
}
test('a normal account and an editor cannot publish or send', async () => {
  const h = harness();
  await assert.rejects(h.call({ op: 'saveDraft', contentId: 'note', content }, null), e => e.code === 'permission-denied');
  for (const role of ['PARENT', 'reader', 'editor']) {
    await assert.rejects(h.call({ op: 'publish' }, role), e => e.code === 'permission-denied');
    await assert.rejects(h.call({ op: 'createCampaign' }, role), e => e.code === 'permission-denied');
  }
});
test('draft edits are versioned, reject lost updates, and never send automatically', async () => {
  const h = harness();
  await prepare(h);
  await h.call({ op: 'saveDraft', contentId: 'note', expectedRevision: 1, content: { ...content, title: 'A fresh beginning' } });
  await assert.rejects(h.call({ op: 'saveDraft', contentId: 'note', expectedRevision: 1, content }), e => e.code === 'aborted');
  assert.equal(h.records.get('contentDrafts/note/revisions/1').title, 'Begin with care');
  assert.equal(h.records.get('publishedContent/note').revision, 1);
  await h.tick();
  assert.equal(h.sends.length, 0);
});
test('retrying createCampaign is idempotent and test sends stay isolated', async () => {
  const h = harness(); const request = await prepare(h);
  await h.call(request); await h.call(request);
  await assert.rejects(h.call({ ...request, audience: 'all' }), e => e.code === 'already-exists');
  await h.tick(); await h.tick();
  assert.equal(h.sends.length, 1);
  assert.equal(h.sends[0].token, 'test-token');
  assert.equal(h.sends[0].notification, undefined);
  assert.equal(h.sends[0].android.priority, 'normal');
  assert.equal(h.records.get('campaigns/send-1').accepted, 1);
  assert.equal(h.records.get('campaigns/send-1').state, 'complete');
});
test('pause, cancellation, opt-out and unpublish stop work that has not been sent', async () => {
  for (const action of ['pause', 'cancelCampaign', 'unpublish', 'opt-out']) {
    const h = harness(); await h.call(await prepare(h));
    if (action === 'opt-out') h.records.get('messageInstallations/test-phone').enabled = false;
    else await h.call({ op: action, paused: true, campaignId: 'send-1', contentId: 'note' });
    await h.tick();
    assert.equal(h.sends.length, 0, action);
  }
});
test('transient failure retries the same campaign and retires an invalid token', async () => {
  const h = harness(); await h.call(await prepare(h));
  h.fail({ code: 'messaging/server-unavailable' }); await h.tick();
  assert.equal(h.records.get('campaigns/send-1/dispatch/test-phone').state, 'retry');
  h.fail({ code: 'messaging/registration-token-not-registered' }); await h.tick();
  assert.equal(h.records.has('messageInstallations/test-phone'), false);
  assert.equal(h.records.get('campaigns/send-1/dispatch/test-phone').state, 'failed');
  assert.equal(h.records.get('campaigns/send-1').failed, 1);
});
test('scheduled publication waits for its date and expires automatically', async () => {
  const h = harness();
  await h.call({ op: 'saveDraft', contentId: 'note', expectedRevision: 0, content });
  await h.call({ op: 'publish', contentId: 'note', revision: 1, startsAt: Date.now() + 60000,
    expiresAt: Date.now() + 120000, timezone: 'Asia/Kolkata' });
  await h.tick(); assert.equal(h.records.has('publishedContent/note'), false);
  h.records.get('messagePublicationJobs/note').dueAt = Date.now() - 1;
  await h.tick(); assert.equal(h.records.has('publishedContent/note'), true);
  h.records.get('publishedContent/note').expiresAt = Date.now() - 1;
  await h.tick(); assert.equal(h.records.has('publishedContent/note'), false);
});

async function prepareNudge(h, patch = {}) {
  const now = Date.now();
  for (const [id, notificationOnly] of [['test-phone', true], ['general-phone', true], ['old-phone', false]]) {
    h.records.set(`messageInstallations/${id}`, { token: id, enabled: true, schemaVersion: 1,
      locale: 'en', notificationOnly, updatedAt: { toMillis: () => now } });
  }
  h.records.set('messageTestDevices/test-phone', { label: 'Test phone' });
  const request = { op: 'createNotificationCampaign', campaignId: 'nudge-1', title: 'A festival note',
    body: 'Your festival offer is ready. Tap to take a look.', destination: 'home',
    audience: 'test', sendAt: now, expiresAt: now + 60000, timezone: 'Asia/Kolkata', ...patch };
  await h.call(request);
  return request;
}

test('notification-only test sends carry their own text and create no published content', async () => {
  const h = harness();
  const request = await prepareNudge(h);
  const estimate = await h.call({ op: 'estimateAudience', kind: 'nudge', audience: 'all' });
  assert.equal(estimate.eligible, 2);
  await h.tick(); await h.tick();
  assert.equal(h.sends.length, 1);
  assert.equal(h.sends[0].token, 'test-phone');
  assert.equal(h.sends[0].notification, undefined);
  assert.equal(h.sends[0].data.kind, 'nudge');
  assert.equal(h.sends[0].data.title, request.title);
  assert.equal(h.sends[0].data.body, request.body);
  assert.equal(h.sends[0].data.destination, 'home');
  assert.equal(h.sends[0].data.contentId, undefined);
  assert.equal(h.sends[0].android.priority, 'normal');
  assert.equal(h.records.get('campaigns/nudge-1').state, 'complete');
  assert.equal([...h.records.keys()].some(key => /^(publishedContent|contentDrafts|messagePublicationJobs)\//.test(key)), false);
});

test('notification-only sends require a publisher and retries preserve the exact request', async () => {
  const h = harness();
  const request = await prepareNudge(h);
  for (const role of [null, 'editor', 'PARENT', 'reader']) {
    await assert.rejects(h.call(request, role), e => e.code === 'permission-denied');
  }
  await h.call(request);
  for (const patch of [{ title: 'Changed' }, { body: 'Changed' }, { audience: 'all' }, { expiresAt: request.expiresAt + 1 }]) {
    await assert.rejects(h.call({ ...request, ...patch }), e => e.code === 'already-exists');
  }
  for (const patch of [{ contentId: 'note' }, { revision: 1 }, { destination: 'offers' }, { destination: 'https://example.com' }]) {
    await assert.rejects(h.call({ ...request, campaignId: 'invalid', ...patch }), e => e.code === 'invalid-argument');
  }
  await prepare(h);
  await assert.rejects(h.call({ ...request, campaignId: 'send-1' }).then(async () => {
    // The nudge ID cannot be reused as a message-linked campaign either.
    await h.call({ op: 'createCampaign', campaignId: 'send-1', contentId: 'note', revision: 1,
      audience: 'test', sendAt: request.sendAt, expiresAt: request.expiresAt - 10000, timezone: request.timezone });
  }), e => e.code === 'already-exists');
});

test('standalone scheduling expiry cancellation pause and opt-out control dispatch', async () => {
  for (const action of ['future', 'expired', 'cancelled', 'paused', 'opt-out']) {
    const h = harness();
    await prepareNudge(h, action === 'future' ? { sendAt: Date.now() + 30000 } : {});
    if (action === 'expired') h.records.get('campaigns/nudge-1').expiresAt = Date.now() - 1;
    if (action === 'cancelled') await h.call({ op: 'cancelCampaign', campaignId: 'nudge-1' });
    if (action === 'paused') await h.call({ op: 'pause', paused: true });
    if (action === 'opt-out') h.records.get('messageInstallations/test-phone').enabled = false;
    await h.tick();
    assert.equal(h.sends.length, 0, action);
    if (action === 'future') {
      h.records.get('campaigns/nudge-1').sendAt = Date.now() - 1;
      await h.tick();
      assert.equal(h.sends.length, 1);
    }
  }
});

test('standalone general sends skip old apps and retry without creating another campaign', async () => {
  const h = harness();
  await prepareNudge(h, { audience: 'all' });
  h.fail({ code: 'messaging/server-unavailable' });
  await h.tick();
  assert.equal(h.records.get('campaigns/nudge-1/dispatch/general-phone').state, 'retry');
  h.fail(null);
  await h.tick();
  assert.equal(h.records.get('campaigns/nudge-1').accepted, 2);
  assert.equal(h.records.get('campaigns/nudge-1/dispatch/old-phone').state, 'skipped');
  assert.equal(h.records.get('campaigns/nudge-1').state, 'complete');
});
