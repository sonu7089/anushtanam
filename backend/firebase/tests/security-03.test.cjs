const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function goalHarness(token = 'device-token', failure) {
  const sends = [], paths = [];
  let options;
  const exports = {};
  const doc = { exists: Boolean(token), get: () => token };
  const reference = { collection: name => { paths.push(name); return reference; },
    doc: name => { paths.push(name); return reference; }, get: async () => doc };
  vm.runInNewContext(fs.readFileSync(require.resolve('../functions/index'), 'utf8'), {
    exports, console: { log() {}, warn() {}, error() {} },
    require: name => {
      if (name === 'firebase-functions/v2/firestore') return { onDocumentCreated: (config, fn) => { options = config; return fn; } };
      if (name === 'firebase-admin/app') return { initializeApp() {} };
      if (name === 'firebase-admin/firestore') return { getFirestore: () => reference };
      if (name === 'firebase-admin/messaging') return { getMessaging: () => ({ send: async message => {
        sends.push(message); if (failure) throw failure;
      } }) };
      if (name === './messages' || name === './privacy') return {};
      throw new Error(name);
    }
  });
  return { sends, paths, options, run: goal => exports.onGoalCreated({ data: { data: () => goal } }) };
}

test('goal pushes never echo free text and stay bounded for multibyte legacy rewards', async () => {
  const h = goalHarness();
  await h.run({ childUid: 'child', rewardDescription: '秘密'.repeat(100000), targetRecitationCount: 108 });
  assert.equal(h.options.retry, false);
  assert.deepEqual(h.paths, ['users', 'child', 'private', 'messaging']);
  assert.equal(h.sends[0].notification.body, 'Open Anushtanam to view your new goal.');
  assert.equal(h.sends[0].data.body, h.sends[0].notification.body);
  assert.ok(Buffer.byteLength(JSON.stringify(h.sends[0])) < 4096);
});

test('missing and invalid registrations do not fail goal delivery', async () => {
  const missing = goalHarness(null);
  await missing.run({ childUid: 'child' });
  assert.equal(missing.sends.length, 0);
  for (const code of ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']) {
    await goalHarness('token', { code }).run({ childUid: 'child' });
  }
});

test('unexpected delivery failures remain observable', async () => {
  const failure = new Error('provider unavailable');
  await assert.rejects(goalHarness('token', failure).run({ childUid: 'child' }), error => error === failure);
});

async function roleHarness(role, flags, user) {
  const changes = [], audit = [];
  const process = { argv: ['node', 'script', 'demo-project', 'admin', role, 'operator', ...flags] };
  const auth = { getUser: async () => user,
    setCustomUserClaims: async (uid, claims) => changes.push({ uid, claims }),
    revokeRefreshTokens: async uid => changes.push({ revoked: uid }) };
  const requireFunctions = name => {
    if (name === 'firebase-admin/app') return { initializeApp() {} };
    if (name === 'firebase-admin/auth') return { getAuth: () => auth };
    if (name === 'firebase-admin/firestore') return { getFirestore: () => ({ collection: () => ({ doc: () => ({
      set: async data => audit.push(data), update: async data => audit.push(data)
    }) }) }) };
    throw new Error(name);
  };
  const source = fs.readFileSync(require.resolve('../scripts/message-role.cjs'), 'utf8');
  await vm.runInNewContext(source, { process, __dirname: '.', console: { log() {}, error() {} },
    require: name => name === 'node:module' ? { createRequire: () => requireFunctions } : require(name) });
  return { changes, audit, process };
}

test('role grants default to dry run, preserve other claims and require MFA', async () => {
  const user = { customClaims: { unrelated: true }, multiFactor: { enrolledFactors: [{}] } };
  assert.equal((await roleHarness('publisher', [], user)).changes.length, 0);
  const blocked = await roleHarness('publisher', ['--apply'], { customClaims: {} });
  assert.equal(blocked.process.exitCode, 1);
  assert.equal(blocked.changes.length, 0);
  const granted = await roleHarness('editor', ['--apply'], user);
  assert.equal(granted.changes[0].claims.unrelated, true);
  assert.equal(granted.changes[0].claims.messageRole, 'editor');
  assert.equal(granted.changes[1].revoked, 'admin');
  assert.equal(granted.audit[0].state, 'requested');
  assert.equal(granted.audit[1].state, 'complete');
});

test('role revocation works without MFA and removes only messageRole', async () => {
  const result = await roleHarness('none', ['--apply'], { customClaims: { messageRole: 'publisher', unrelated: true } });
  assert.equal(result.changes[0].claims.messageRole, undefined);
  assert.equal(result.changes[0].claims.unrelated, true);
  assert.equal(result.changes[1].revoked, 'admin');
});
