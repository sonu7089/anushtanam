const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, deleteDoc, setDoc, updateDoc, writeBatch, serverTimestamp } = require('firebase/firestore');
const { publish } = require('./backup-fixtures.cjs');
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-session-counting',
  firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } }); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
const db = () => env.authenticatedContext('owner').firestore();
const summary = { sessionProjectionVersion: 2, sessionProjectionRevision: 1, totalSessions: 0, correctSessions: 0, scoreSum: 0, bestScore: 0, daily: {} };
const session = { type: 'session', id: 'one', entryId: null, mantraId: 'gayatri', mode: 'NORMAL',
  overallScore: .9, correctness: .9, completeness: .9, pronunciation: .9,
  wordScoresJson: '', transcription: '', isCorrect: true, durationMs: 1000, createdAt: 0,
  projectionDate: '1970-01-01', projectionZoneId: 'UTC', childGoalEligible: true };

test('after cutover a canonical session and its summary must commit together', async () => {
  const client = db();
  await setDoc(doc(client, 'progress/owner'), summary);
  await assertFails(publish(client, 'owner', 'practice', session));
  const batch = writeBatch(client);
  batch.set(doc(client, 'users/owner/practiceRecords/session_one'), session);
  batch.set(doc(client, 'users/owner/backupChanges/practice_session_one'), {
    type: 'practice', id: 'session_one', value: session, changedAt: serverTimestamp()
  });
  batch.set(doc(client, 'progress/owner'), { ...summary, sessionProjectionRevision: 2, totalSessions: 1 });
  await assertSucceeds(batch.commit());
  await assertSucceeds(publish(client, 'owner', 'practice', session));
});

test('cutover rejects anonymous increments downgrade and deletion but permits checkpoints', async () => {
  const client = db(), ref = doc(client, 'progress/owner');
  await setDoc(ref, summary);
  await assertFails(updateDoc(ref, { totalSessions: 1 }));
  await assertFails(setDoc(ref, { totalSessions: 0 }));
  await assertFails(deleteDoc(ref));
  await assertFails(updateDoc(ref, { sessionProjectionVersion: 1, sessionProjectionRevision: 2 }));
  await assertSucceeds(updateDoc(ref, { syncCheckpoints: { device: 5 }, updatedAt: serverTimestamp() }));
});

test('pre-repair projections are private immutable archives', async () => {
  const client = db(), ref = doc(client, 'users/owner/projectionRepairs/v2_progress');
  await assertSucceeds(setDoc(ref, { version: 2, kind: 'progress', value: { totalSessions: 99 }, createdAt: serverTimestamp() }));
  await assertSucceeds(getDoc(ref));
  await assertFails(getDoc(doc(env.authenticatedContext('other').firestore(), ref.path)));
  await assertFails(updateDoc(ref, { value: { totalSessions: 0 } }));
  await assertFails(deleteDoc(ref));
});

test('occurrence metadata must be complete and only normal accepted audio is reward eligible', async () => {
  const client = db();
  for (const changes of [{ projectionDate: 'bad' }, { projectionZoneId: null },
    { childGoalEligible: 'true' }, { mode: 'PRACTICE' }, { isCorrect: false }]) {
    await assertFails(publish(client, 'owner', 'practice', { ...session, ...changes }));
  }
  const { projectionZoneId, ...incomplete } = session;
  await assertFails(publish(client, 'owner', 'practice', incomplete));
  await assertSucceeds(publish(client, 'owner', 'practice', { ...session, mode: 'PRACTICE', childGoalEligible: false }));
});
