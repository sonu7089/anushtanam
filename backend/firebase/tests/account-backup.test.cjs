const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, writeBatch, serverTimestamp } = require('firebase/firestore');
let env;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-account-backup',
  firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } }); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
const db = uid => env.authenticatedContext(uid).firestore();
const record = (overrides = {}) => ({ id: 'preference_minimum_PRATAH', updatedAtMillis: 100,
  operationId: 'a', deleted: false, dataJson: '{"value":108}', ...overrides });
function publish(client, value, feedValue = value) {
  const batch = writeBatch(client);
  batch.set(doc(client, `users/owner/accountRecords/${value.id}`), value);
  batch.set(doc(client, `users/owner/backupChanges/account_${value.id}`), {
    type: 'account', id: value.id, value: feedValue, changedAt: serverTimestamp()
  });
  return batch.commit();
}
test('account records are private, feed-backed, and reject stale edits and malformed envelopes', async () => {
  const owner = db('owner'), value = record();
  await assertFails(setDoc(doc(owner, `users/owner/accountRecords/${value.id}`), value));
  await assertFails(publish(db('other'), value));
  await assertSucceeds(publish(owner, value));
  await assertSucceeds(publish(owner, value));
  await assertFails(getDoc(doc(db('parent'), `users/owner/accountRecords/${value.id}`)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `users/owner/accountRecords/${value.id}`)));
  await assertFails(publish(owner, record({ updatedAtMillis: 0 })));
  await assertFails(publish(owner, record({ updatedAtMillis: 101, role: 'PARENT' })));
  await assertFails(publish(owner, record({ updatedAtMillis: 101 }), value));
  await assertSucceeds(publish(owner, record({ updatedAtMillis: 100, operationId: 'b', dataJson: '{"value":216}' })));
});
test('a draft tombstone survives a late write from another device', async () => {
  const owner = db('owner'), value = record({ id: 'draft_one', updatedAtMillis: 500, dataJson: '{"input":"12"}' });
  await assertSucceeds(publish(owner, value));
  await assertSucceeds(publish(owner, { ...value, updatedAtMillis: 100, deleted: true, dataJson: '{"deleted":true}' }));
  await assertFails(publish(owner, { ...value, updatedAtMillis: 999 }));
});
test('account deletion barrier also blocks supplemental settings', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'accountDeletionRequests/owner'), { state: 'pending' });
  });
  await assertFails(publish(db('owner'), record()));
});
