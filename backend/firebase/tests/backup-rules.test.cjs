const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, writeBatch, serverTimestamp, Timestamp } = require('firebase/firestore');
let env;
const fixtures = require('./backup-fixtures.cjs');
const user = uid => env.authenticatedContext(uid).firestore();
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-anushtanam-backup',
  firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } }); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

const performance = () => ({ type: 'session', id: 'session-one', entryId: null, mantraId: 'gayatri', mode: 'PRACTICE',
  overallScore: .86, correctness: .9, completeness: .8, pronunciation: .88,
  wordScoresJson: 'om:0.91', transcription: 'om', isCorrect: false, durationMs: 24000, createdAt: Date.now() });

async function publish(db, value) {
  const id = `${value.type}_${value.id}`;
  const batch = writeBatch(db);
  batch.set(doc(db, `users/individual/practiceRecords/${id}`), value);
  batch.set(doc(db, `users/individual/backupChanges/practice_${id}`), {
    type: 'practice', id, value, changedAt: serverTimestamp()
  });
  return batch.commit();
}

test('full performance and restore feed are private and immutable', async () => {
  const db = user('individual'), value = performance();
  await assertSucceeds(publish(db, value));
  await assertSucceeds(publish(db, value));
  await assertFails(publish(db, { ...value, overallScore: 1 }));
  for (const path of ['practiceRecords/session_session-one', 'backupChanges/practice_session_session-one']) {
    await assertFails(getDoc(doc(user('parent'), `users/individual/${path}`)));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `users/individual/${path}`)));
  }
  assert.equal((await getDoc(doc(db, 'users/individual/practiceRecords/session_session-one'))).data().wordScoresJson, 'om:0.91');
});

test('restore feed rejects forged contents and client timestamps', async () => {
  const db = user('individual'), value = performance();
  await assertSucceeds(publish(db, value));
  const ref = doc(db, 'users/individual/backupChanges/practice_session_session-one');
  await assertFails(setDoc(ref, { type: 'practice', id: 'session_session-one', value: { ...value, durationMs: 0 }, changedAt: serverTimestamp() }));
  await assertFails(setDoc(ref, { type: 'practice', id: 'session_session-one', value, changedAt: Timestamp.fromMillis(1) }));
});

test('an observed Sandhya survives a missed reminder on another phone', async () => {
  const db = user('individual');
  const value = { type: 'sandhya', id: '20260910_SAYAM', dateKey: '20260910', period: 'SAYAM',
    scheduledEpochMillis: 100, status: 'NOTIFIED', observedEpochMillis: null };
  await assertSucceeds(publish(db, value));
  await assertSucceeds(publish(db, { ...value, status: 'OBSERVED', observedEpochMillis: 120 }));
  await assertFails(publish(db, { ...value, status: 'MISSED' }));
});

test('rejected edits and their conflict survive an atomic upload and resolution', async () => {
  const db = user('individual'), path = 'users/individual/japaEntries/shared';
  const first = fixtures.entry({ id: 'shared', quantity: 540, revision: 2, operationId: 'first', updatedAtMillis: 100 });
  const second = { ...first, quantity: 500, operationId: 'second', updatedAtMillis: 90 };
  await assertSucceeds(fixtures.publish(db, 'individual', 'entry', first));
  const conflict = { id: 'shared:first:second', entryId: 'shared', first, second, resolved: false };
  const batch = writeBatch(db);
  batch.set(doc(db, `${path}/revisions/second`), second);
  batch.set(doc(db, 'users/individual/japaConflicts/shared:first:second'), conflict);
  batch.set(doc(db, 'users/individual/backupChanges/conflict_shared:first:second'), {
    type: 'conflict', id: conflict.id, value: conflict, changedAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
  await assertFails(getDoc(doc(user('parent'), 'users/individual/japaConflicts/shared:first:second')));
  await assertFails(updateDoc(doc(db, 'users/individual/japaConflicts/shared:first:second'), { resolved: true }));
  await assertSucceeds(fixtures.publish(db, 'individual', 'conflict', { ...conflict, resolved: true }));
  await assertFails(fixtures.publish(db, 'individual', 'conflict', conflict));
  assert.equal((await getDoc(doc(db, path))).data().quantity, 540);
});
