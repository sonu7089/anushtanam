// Reproduces current validation gaps against a LOCAL emulator only.
// These assertions document current behavior; they must change when rules are fixed.
const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const backendRequire = createRequire(path.resolve(__dirname, '../../backend/firebase/package.json'));
const { initializeTestEnvironment, assertSucceeds, assertFails } = backendRequire('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, writeBatch, serverTimestamp } = backendRequire('firebase/firestore');
const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!host || !/^127\.0\.0\.1:\d+$/.test(host)) throw new Error('Set FIRESTORE_EMULATOR_HOST to a loopback emulator; live projects are forbidden');
let env;
before(async () => {
  const [hostname, port] = host.split(':');
  env = await initializeTestEnvironment({ projectId: 'demo-perf-audit', firestore: {
    host: hostname, port: Number(port),
    rules: fs.readFileSync(path.resolve(__dirname, '../../backend/firebase/firestore.rules'), 'utf8')
  }});
});
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

test('AUDIT: child can change extra goal fields and write a negative count', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'goals/example'), {
      parentUid: 'parent', childUid: 'child', rewardType: 'OTHER', rewardDescription: 'A book',
      targetRecitationCount: 108, currentRecitationCount: 0, isCompleted: false, createdAt: serverTimestamp()
    });
  });
  const db = env.authenticatedContext('child').firestore();
  await assertSucceeds(updateDoc(doc(db, 'goals/example'), {
    currentRecitationCount: -5, isCompleted: true, createdAt: null, unexpectedField: 'allowed'
  }));
  const result = (await getDoc(doc(db, 'goals/example'))).data();
  assert.equal(result.currentRecitationCount, -5);
  assert.equal(result.createdAt, null);
});

test('AUDIT: malformed owner ledger date/timezone passes canonical feed validation', async () => {
  const db = env.authenticatedContext('owner').firestore();
  const value = { id: 'bad-date', quantity: 108, revision: 1, mantraId: 'gayatri',
    operationId: 'op-one', dateKey: 'not-a-date', zoneId: 'not/a/zone', period: 'SAYAM',
    source: 'SANDHYA', createdAtMillis: 1, updatedAtMillis: 1, targetId: null, vowId: null };
  const batch = writeBatch(db);
  batch.set(doc(db, 'users/owner/japaEntries/bad-date'), value);
  batch.set(doc(db, 'users/owner/backupChanges/entry_bad-date'), {
    type: 'entry', id: 'bad-date', value, changedAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
});

test('AUDIT: canonical entry creation does not require a companion restore-feed write', async () => {
  const db = env.authenticatedContext('owner').firestore();
  await assertSucceeds(setDoc(doc(db, 'users/owner/japaEntries/missing-feed'), {
    id: 'missing-feed', quantity: 1, revision: 1, mantraId: 'gayatri', operationId: 'op'
  }));
  assert.equal((await getDoc(doc(db, 'users/owner/backupChanges/entry_missing-feed'))).exists(), false);
});

test('control: another account still cannot write the owner ledger', async () => {
  await assertFails(setDoc(doc(env.authenticatedContext('stranger').firestore(), 'users/owner/japaEntries/other'), {
    id: 'other', quantity: 1, revision: 1, mantraId: 'gayatri', operationId: 'op'
  }));
});
