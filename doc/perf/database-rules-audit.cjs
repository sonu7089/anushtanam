// Regression rejection tests for the original successful-invalid-write probes.
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

test('DB-03: child cannot change extra goal fields or write a negative count', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'goals/example'), {
      parentUid: 'parent', childUid: 'child', rewardType: 'OTHER', rewardDescription: 'A book',
      targetRecitationCount: 108, currentRecitationCount: 0, isCompleted: false, createdAt: serverTimestamp()
    });
  });
  const db = env.authenticatedContext('child').firestore();
  await assertFails(updateDoc(doc(db, 'goals/example'), {
    currentRecitationCount: -5, isCompleted: true, createdAt: null, unexpectedField: 'allowed'
  }));
  const result = (await getDoc(doc(db, 'goals/example'))).data();
  assert.equal(result.currentRecitationCount, 0);
  assert.notEqual(result.createdAt, null);
});

test('DB-04: malformed owner ledger date is rejected even with a matching feed', async () => {
  const db = env.authenticatedContext('owner').firestore();
  const value = { id: 'bad-date', quantity: 108, revision: 1, mantraId: 'gayatri',
    operationId: 'op-one', dateKey: 'not-a-date', zoneId: 'not/a/zone', period: 'SAYAM',
    source: 'SANDHYA', createdAtMillis: 1, updatedAtMillis: 1, targetId: null, vowId: null };
  const batch = writeBatch(db);
  batch.set(doc(db, 'users/owner/japaEntries/bad-date'), value);
  batch.set(doc(db, 'users/owner/backupChanges/entry_bad-date'), {
    type: 'entry', id: 'bad-date', value, changedAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test('DB-04: valid canonical entry requires a companion restore-feed write', async () => {
  const db = env.authenticatedContext('owner').firestore();
  const { entry } = require('../../backend/firebase/tests/backup-fixtures.cjs');
  await assertFails(setDoc(doc(db, 'users/owner/japaEntries/missing-feed'), entry({ id: 'missing-feed' })));
  assert.equal((await getDoc(doc(db, 'users/owner/backupChanges/entry_missing-feed'))).exists(), false);
});

test('control: another account still cannot write the owner ledger', async () => {
  await assertFails(setDoc(doc(env.authenticatedContext('stranger').firestore(), 'users/owner/japaEntries/other'), {
    id: 'other', quantity: 1, revision: 1, mantraId: 'gayatri', operationId: 'op'
  }));
});
