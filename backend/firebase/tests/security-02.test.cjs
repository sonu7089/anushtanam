const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp, deleteField } = require('firebase/firestore');
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-security-02',
  firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } }); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
const db = (uid = 'owner') => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'google.com' } }).firestore();
const progress = () => ({ totalSessions: 1, correctSessions: 1, scoreSum: .9, bestScore: .9,
  daily: { '2026-09-13': { n: 1, c: 1, s: .9 } }, sessionProjectionVersion: 2,
  sessionProjectionRevision: 1, projectionInvalidRecords: 0, legacyDatePolicy: 'ledger-date-or-UTC', updatedAt: serverTimestamp() });
const target = () => ({ stableId: 'target', mantraId: 'gayatri', startedAtMillis: 1, baselineCount: 0,
  targetRecitationCount: 10, currentRecitationCount: 0, rewardType: 'OTHER', rewardDescription: '',
  isCompleted: false, updatedAt: serverTimestamp() });
const vow = () => ({ stableId: 'vow', mantraId: 'gayatri', baselineCount: 0, mantraAksharaCount: 24,
  targetJapaCount: 2400000, currentJapaCount: 0, startedAtMillis: 1, completedAtMillis: null,
  isActive: true, isCompleted: false, updatedAt: serverTimestamp() });

test('mirrors validate both creates and updates and preserve explicit target clearing', async () => {
  for (const [collection, value, bad] of [
    ['selfTargets', target, [{ targetRecitationCount: 0 }, { currentRecitationCount: 11 }, { rewardDescription: 'x'.repeat(200001) }]],
    ['purashcharana', vow, [{ mantraAksharaCount: '24' }, { currentJapaCount: -1 }, { completedAtMillis: 2 }]]
  ]) {
    const ref = doc(db(), `${collection}/owner`);
    await assertFails(setDoc(ref, { ...value(), injected: {} }));
    await assertSucceeds(setDoc(ref, value()));
    for (const change of [...bad, { isCompleted: true }, { updatedAt: 0 }])
      await assertFails(updateDoc(ref, change));
    await assertFails(getDoc(doc(db('other'), ref.path)));
    await assertFails(setDoc(doc(db('other'), ref.path), value()));
  }
  await assertSucceeds(deleteDoc(doc(db(), 'selfTargets/owner')));
  await assertSucceeds(updateDoc(doc(db(), 'purashcharana/owner'), { isActive: false, updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(doc(db(), 'purashcharana/owner')));
});

test('progress rejects malformed creation and revisioned updates', async () => {
  const ref = doc(db(), 'progress/owner');
  for (const bad of [{ extra: 'payload' }, { totalSessions: -1 }, { correctSessions: 2 }, { scoreSum: 2 },
    { bestScore: 2 }, { daily: [] }, { daily: Object.fromEntries(Array.from({ length: 61 }, (_, i) => [i, {}])) },
    { sessionProjectionVersion: 3 }, { projectionInvalidRecords: -1 }])
    await assertFails(setDoc(ref, { ...progress(), ...bad }));
  await assertSucceeds(setDoc(ref, progress()));
  await assertFails(updateDoc(ref, { sessionProjectionRevision: 2, totalSessions: 'bad' }));
  await assertFails(deleteDoc(ref));
});

test('empty and legacy bootstrap barriers freeze writers until a validated repair', async () => {
  const ref = doc(db(), 'progress/owner');
  const barrier = { sessionProjectionVersion: 1, sessionProjectionRevision: 0 };
  await assertFails(setDoc(ref, { ...barrier, arbitrary: true }));
  await assertSucceeds(setDoc(ref, barrier));
  await assertFails(updateDoc(ref, { totalSessions: 5 }));
  await assertSucceeds(setDoc(ref, progress()));
  await assertFails(setDoc(ref, barrier));
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), ref.path), { totalSessions: 99 }));
  await assertFails(updateDoc(ref, { ...barrier, totalSessions: 0 }));
  await assertSucceeds(updateDoc(ref, barrier));
  await assertSucceeds(setDoc(ref, progress()));
});

test('profiles pin identity, validate fields, and keep tokens private even before migration', async () => {
  const ref = doc(db(), 'users/owner');
  const profile = { role: 'INDIVIDUAL', displayName: 'Owner', createdAt: serverTimestamp() };
  await assertFails(setDoc(ref, { ...profile, linkCode: '123456789012' }));
  await assertSucceeds(setDoc(ref, profile));
  for (const bad of [{ displayName: 'x'.repeat(101) }, { photoUrl: 'http://example.test' }, { fcmToken: 'secret' },
    { linkCode: '123456789012' }, { role: 'PARENT' }, { createdAt: 0 }, { arbitrary: true }])
    await assertFails(updateDoc(ref, bad));
  await assertSucceeds(updateDoc(ref, { photoUrl: 'https://example.test/photo', photoSource: 'GOOGLE', syncCheckpoints: { device: 1 } }));
  const privateRef = doc(db(), 'users/owner/private/messaging');
  await assertSucceeds(setDoc(privateRef, { fcmToken: 'secret' }));
  await assertFails(setDoc(privateRef, { fcmToken: 'x'.repeat(4097) }));
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), 'links/parent_owner'), { status: 'APPROVED' });
    await updateDoc(doc(ctx.firestore(), ref.path), { fcmToken: 'legacy-secret' });
  });
  await assertFails(getDoc(doc(db('parent'), privateRef.path)));
  await assertFails(getDoc(doc(db('parent'), ref.path)));
  await assertSucceeds(updateDoc(ref, { fcmToken: deleteField() }));
  await assertSucceeds(getDoc(doc(db('parent'), ref.path)));
});

test('token migration defaults to dry-run, preserves newer tokens, and is restartable', async () => {
  const assert = require('node:assert/strict');
  const execFile = require('node:util').promisify(require('node:child_process').execFile);
  if (!/^127\.0\.0\.1:\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST || ''))
    throw new Error('Migration test requires a loopback emulator');
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), 'users/owner'), { role: 'INDIVIDUAL', displayName: 'Owner', fcmToken: 'old' });
    await setDoc(doc(ctx.firestore(), 'users/other'), { role: 'INDIVIDUAL', displayName: 'Other', fcmToken: 'legacy' });
    await setDoc(doc(ctx.firestore(), 'users/other/private/messaging'), { fcmToken: 'newer' });
  });
  const run = async (...args) => JSON.parse((await execFile(process.execPath,
    ['scripts/migrate-private-tokens.cjs', 'demo-security-02', ...args])).stdout);
  assert.equal((await run()).moved, 0);
  assert.equal((await getDoc(doc(db(), 'users/owner'))).get('fcmToken'), 'old');
  assert.equal((await run('--apply')).moved, 2);
  assert.equal((await getDoc(doc(db(), 'users/owner/private/messaging'))).get('fcmToken'), 'old');
  assert.equal((await getDoc(doc(db('other'), 'users/other/private/messaging'))).get('fcmToken'), 'newer');
  assert.equal((await getDoc(doc(db(), 'users/owner'))).get('fcmToken'), undefined);
  assert.equal((await run('--apply')).found, 0);
});
