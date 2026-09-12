const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, writeBatch, serverTimestamp } = require('firebase/firestore');
const { entry, goal, publish } = require('./backup-fixtures.cjs');
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-backup-validation',
  firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } }); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
const user = uid => env.authenticatedContext(uid).firestore();

test('parent creates and edits valid assigned goals while invalid targets and identity changes fail', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), 'links/parent_child'), { status: 'APPROVED' }));
  const db = user('parent');
  const value = { parentUid: 'parent', childUid: 'child', rewardType: 'OTHER', rewardDescription: 'A book',
    targetRecitationCount: 108, currentRecitationCount: 0, isCompleted: false, createdAt: serverTimestamp() };
  for (const changes of [{ targetRecitationCount: 0 }, { targetRecitationCount: 2147483648 },
    { currentRecitationCount: 1 }, { createdAt: null }, { extra: true }, { childUid: 'unlinked' }]) {
    await assertFails(setDoc(doc(db, 'goals/invalid'), { ...value, ...changes }));
  }
  const ref = doc(db, 'goals/valid');
  await assertSucceeds(setDoc(ref, value));
  await assertSucceeds(updateDoc(ref, { rewardDescription: 'A trip', targetRecitationCount: 200 }));
  await assertFails(updateDoc(ref, { createdAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { childUid: 'other' }));
  await assertFails(updateDoc(ref, { targetRecitationCount: -1 }));
});

test('child can only change consistent, bounded progress; every other field is protected', async () => {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), 'goals/example'), {
    parentUid: 'parent', childUid: 'child', rewardType: 'OTHER', rewardDescription: 'A book',
    targetRecitationCount: 108, currentRecitationCount: 0, isCompleted: false, createdAt: serverTimestamp()
  }));
  const ref = doc(user('child'), 'goals/example');
  for (const change of [
    { currentRecitationCount: -1 }, { currentRecitationCount: 1.5 }, { currentRecitationCount: 109, isCompleted: true },
    { currentRecitationCount: 108 }, { isCompleted: true }, { createdAt: null }, { surprise: true },
    { rewardType: 'GIFT' }, { rewardDescription: 'Changed' }, { targetRecitationCount: 1 },
    { parentUid: 'someone' }, { childUid: 'someone' }, { schemaVersion: 2 }
  ]) await assertFails(updateDoc(ref, change));
  await assertSucceeds(updateDoc(ref, { currentRecitationCount: 1 }));
  await assertSucceeds(updateDoc(ref, { currentRecitationCount: 108, isCompleted: true }));
  await assertFails(updateDoc(doc(user('stranger'), 'goals/example'), { currentRecitationCount: 0, isCompleted: false }));
});

test('entry schemas reject missing, unknown, mistyped and inconsistent values atomically', async () => {
  const db = user('owner');
  const invalid = [ { quantity: -1 }, { quantity: .5 }, { revision: 0 }, { revision: 1.5 },
    { dateKey: 'not-a-date' }, { dateKey: '20261301' }, { zoneId: null }, { source: 'OTHER' },
    { period: null }, { source: 'AUDIO_ACCEPTED', period: null }, { sessionStableId: 'session' },
    { targetId: 4 }, { updatedAtMillis: -1 }, { createdAtMillis: 999999999999999 },
    { mantraId: '' }, { operationId: '' }, { extra: true }, { schemaVersion: 2 } ];
  for (const change of invalid) await assertFails(publish(db, 'owner', 'entry', entry(change)));
  for (const key of Object.keys(entry())) {
    const value = entry(); delete value[key];
    await assertFails(publish(db, 'owner', 'entry', value, 'entry-one'));
  }
  await assertSucceeds(publish(db, 'owner', 'entry', entry()));
  await assertSucceeds(publish(db, 'owner', 'entry', entry())); // Installed v1 client replay.
  await assertSucceeds(publish(db, 'owner', 'entry', entry({ schemaVersion: 1 })));
  await assertSucceeds(publish(db, 'owner', 'entry', entry({ quantity: 500, revision: 2 })));
});

test('all canonical types require a current matching feed, including updates', async () => {
  const db = user('owner');
  const log = { type: 'sandhya', id: '20260910_SAYAM', dateKey: '20260910', period: 'SAYAM',
    scheduledEpochMillis: 100, status: 'NOTIFIED', observedEpochMillis: null };
  for (const [type, collection, value, id] of [
    ['entry', 'japaEntries', entry(), 'entry-one'], ['goal', 'japaGoals', goal(), 'goal-one'],
    ['practice', 'practiceRecords', log, 'sandhya_20260910_SAYAM']
  ]) {
    const ref = doc(db, `users/owner/${collection}/${id}`);
    await assertFails(setDoc(ref, value));
    await assertSucceeds(publish(db, 'owner', type, value, id));
    // A stale feed from an earlier request cannot authorize even an identical rewrite.
    await assertFails(setDoc(ref, value));
  }
  await assertFails(updateDoc(doc(db, 'users/owner/japaEntries/entry-one'), { quantity: 1, revision: 2 }));
  await assertFails(updateDoc(doc(db, 'users/owner/japaGoals/goal-one'), { endedAtMillis: 100 }));
  await assertFails(updateDoc(doc(db, 'users/owner/practiceRecords/sandhya_20260910_SAYAM'), { status: 'OBSERVED', observedEpochMillis: 200 }));
  await assertSucceeds(publish(db, 'owner', 'entry', entry({ quantity: 500, revision: 2 })));
});

test('goals and practice reject narrowing overflow, missing fields and nonfinite scores', async () => {
  const db = user('owner');
  for (const change of [{ targetCount: 2147483648 }, { baselineCount: -1 }, { targetCount: 0 },
    { startedAtMillis: null }, { kind: 'unknown' }, { endedAtMillis: 'bad' }, { schemaVersion: 2 }]) {
    await assertFails(publish(db, 'owner', 'goal', goal(change)));
  }
  const session = { type: 'session', id: 's', entryId: null, mantraId: 'gayatri', mode: 'NORMAL',
    overallScore: .9, correctness: .9, completeness: 1, pronunciation: .9, isCorrect: true,
    wordScoresJson: '', transcription: '', durationMs: 1, createdAt: 1 };
  for (const change of [{ overallScore: NaN }, { correctness: Infinity }, { completeness: 1.1 },
    { durationMs: -1 }, { entryId: 12 }, { isCorrect: 'yes' }, { createdAt: null }]) {
    await assertFails(publish(db, 'owner', 'practice', { ...session, ...change }));
  }
  await assertSucceeds(publish(db, 'owner', 'practice', session));
  const { rewardType, rewardDescription, ...vow } = goal({ id: 'v', kind: 'vow', targetCount: 2400000,
    aksharas: 24, completedAtMillis: null });
  await assertSucceeds(publish(db, 'owner', 'goal', vow));
});
