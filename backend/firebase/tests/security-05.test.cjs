const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp } = require('firebase/firestore');
const requireFunctions = require('node:module').createRequire(require.resolve('../functions/package.json'));
const { initializeApp, deleteApp } = requireFunctions('firebase-admin/app');
const { getFirestore, FieldValue, FieldPath, Timestamp } = requireFunctions('firebase-admin/firestore');
const { getAuth } = requireFunctions('firebase-admin/auth');
const { eraseAccount, redactSpeechPage, RETENTION_MS } = require('../functions/privacy-service');
const { publish } = require('./backup-fixtures.cjs');
let env, app, admin, auth;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-security-05', firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } });
  app = initializeApp({ projectId: 'demo-security-05' }, 'privacy-tests');
  admin = getFirestore(app); auth = getAuth(app);
});
after(async () => { await env?.cleanup(); if (app) await deleteApp(app); });
beforeEach(async () => {
  await env.clearFirestore();
  const users = await auth.listUsers();
  if (users.users.length) await auth.deleteUsers(users.users.map(u => u.uid));
});
const db = (uid = 'owner', age = 0, provider = 'google.com') => env.authenticatedContext(uid, {
  auth_time: Math.floor(Date.now() / 1000) - age, firebase: { sign_in_provider: provider }
}).firestore();
const request = () => ({ state: 'pending', requestedAt: serverTimestamp() });
const session = (createdAt, extra = {}) => ({ type: 'session', id: 'one', entryId: null, mantraId: 'gayatri',
  mode: 'PRACTICE', overallScore: .9, correctness: .9, completeness: .9, pronunciation: .9,
  wordScoresJson: 'om:0.9', transcription: 'om', isCorrect: true, durationMs: 1000, createdAt, ...extra });

test('deletion requires fresh own-account credentials, cannot be canceled, and blocks stale-token writes', async () => {
  const owner = db();
  await assertFails(setDoc(doc(db('owner', 600), 'accountDeletionRequests/owner'), request()));
  await assertFails(setDoc(doc(db('owner', -600), 'accountDeletionRequests/owner'), request()));
  await assertFails(setDoc(doc(db('owner', 0, 'anonymous'), 'accountDeletionRequests/owner'), request()));
  await assertFails(setDoc(doc(db('other'), 'accountDeletionRequests/owner'), request()));
  await assertFails(setDoc(doc(owner, 'accountDeletionRequests/owner'), { ...request(), uid: 'other' }));
  await assertSucceeds(setDoc(doc(owner, 'accountDeletionRequests/owner'), request()));
  await assertSucceeds(getDoc(doc(owner, 'accountDeletionRequests/owner')));
  await assertFails(getDoc(doc(db('other'), 'accountDeletionRequests/owner')));
  await assertFails(deleteDoc(doc(owner, 'accountDeletionRequests/owner')));
  await assertFails(updateDoc(doc(owner, 'accountDeletionRequests/owner'), { state: 'complete' }));
  await assertFails(setDoc(doc(owner, 'users/owner'), { role: 'INDIVIDUAL', displayName: 'Owner', createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(owner, 'users/owner/private/messaging'), { fcmToken: 'token' }));
  await assertSucceeds(setDoc(doc(db('password-user', 0, 'password'), 'accountDeletionRequests/password-user'), request()));
});

test('a linked account cannot recreate goals or access a profile during erasure', async () => {
  await admin.doc('users/owner').set({ role: 'CHILD', displayName: 'Child' });
  await admin.doc('users/parent').set({ role: 'PARENT', displayName: 'Parent' });
  await admin.doc('links/parent_owner').set({ parentUid: 'parent', childUid: 'owner', status: 'APPROVED' });
  const parent = db('parent');
  await assertSucceeds(getDoc(doc(parent, 'users/owner')));
  await assertSucceeds(setDoc(doc(db(), 'accountDeletionRequests/owner'), request()));
  await assertFails(getDoc(doc(parent, 'users/owner')));
  await assertFails(setDoc(doc(parent, 'goals/new'), { parentUid: 'parent', childUid: 'owner', rewardType: 'OTHER',
    rewardDescription: '', targetRecitationCount: 10, currentRecitationCount: 0, isCompleted: false, createdAt: serverTimestamp() }));
});

test('erasure resumes after partial failure, removes nested data and all relationships, and deletes Auth last', async () => {
  await auth.createUser({ uid: 'owner' });
  await auth.createUser({ uid: 'child' });
  const data = {
    'accountDeletionRequests/owner': { state: 'pending' }, 'users/owner': { role: 'PARENT' },
    'users/child': { role: 'CHILD' }, 'users/owner/japaEntries/e/revisions/r': { secret: true },
    'users/owner/practiceRecords/session_one': session(1), 'users/owner/backupChanges/practice_session_one': { value: session(1) },
    'users/owner/private/messaging': { fcmToken: 'secret' }, 'users/owner/projectionRepairs/a': { secret: true },
    'progress/owner': {}, 'selfTargets/owner': {}, 'purashcharana/owner': {}, 'familyInviteState/owner': {},
    'links/owner_child': { parentUid: 'owner', childUid: 'child' }, 'goals/g': { parentUid: 'owner', childUid: 'child' },
    'childCodes/123': { uid: 'owner' }, 'childCodes/456': { childUid: 'owner' },
    'familyInvites/a': { childUid: 'owner' }, 'familyInvites/b': { childUid: 'child', usedBy: 'owner' },
    'messageInstallations/installation': { uid: 'owner' }, 'messageTestDevices/installation': {},
    'messageTestDevices/orphan': { uid: 'owner' }
  };
  for (const [path, value] of Object.entries(data)) await admin.doc(path).set(value);
  const wrapper = { updateUser: (...args) => auth.updateUser(...args), revokeRefreshTokens: (...args) => auth.revokeRefreshTokens(...args),
    deleteUser: async () => { assert.equal((await admin.doc('users/owner').get()).exists, false); throw new Error('transient'); } };
  await assert.rejects(eraseAccount(admin, wrapper, 'owner', { FieldValue, Timestamp }), /transient/);
  assert.equal((await admin.doc('accountDeletionRequests/owner').get()).get('state'), 'pending');
  assert.equal((await auth.getUser('owner')).disabled, true);
  await eraseAccount(admin, auth, 'owner', { FieldValue, Timestamp });
  await eraseAccount(admin, auth, 'owner', { FieldValue, Timestamp });
  for (const path of Object.keys(data).filter(p => !['users/child', 'accountDeletionRequests/owner'].includes(p))) {
    assert.equal((await admin.doc(path).get()).exists, false, path);
  }
  assert.equal((await admin.doc('users/child').get()).exists, true);
  assert.equal((await auth.getUser('child')).uid, 'child');
  await assert.rejects(auth.getUser('owner'), e => e.code === 'auth/user-not-found');
  assert.equal((await admin.doc('accountDeletionRequests/owner').get()).get('state'), 'complete');
});

test('retention strips canonical and orphan feed speech, preserves scores, and prevents re-upload', async () => {
  const now = Date.now();
  const old = session(now - RETENTION_MS - 1000);
  const recent = session(now, { id: 'recent' });
  await admin.doc('users/owner/practiceRecords/session_one').set(old);
  await admin.doc('users/owner/practiceRecords/session_recent').set(recent);
  await admin.doc('users/owner/backupChanges/practice_orphan').set({ type: 'practice', id: 'orphan', value: old });
  await redactSpeechPage(admin, 'practiceRecords', { FieldValue, FieldPath }, now);
  await redactSpeechPage(admin, 'backupChanges', { FieldValue, FieldPath }, now);
  const expected = { ...old, transcription: '', wordScoresJson: '' };
  assert.deepEqual((await admin.doc('users/owner/practiceRecords/session_one').get()).data(), expected);
  assert.deepEqual((await admin.doc('users/owner/backupChanges/practice_session_one').get()).get('value'), expected);
  assert.deepEqual((await admin.doc('users/owner/backupChanges/practice_orphan').get()).get('value'), expected);
  assert.deepEqual((await admin.doc('users/owner/practiceRecords/session_recent').get()).data(), recent);
  await assertFails(publish(db('other'), 'other', 'practice', old));
  await assertSucceeds(publish(db('other'), 'other', 'practice', expected));
  await assertFails(publish(db(), 'owner', 'practice', old));
});

test('retention resumes past a full page with equal timestamps and does not recreate deleted account data', async () => {
  const now = Date.now(), createdAt = now - RETENTION_MS - 1000;
  const batch = admin.batch();
  for (let i = 0; i < 201; i++) {
    const id = String(i).padStart(3, '0');
    batch.set(admin.doc(`users/owner/practiceRecords/session_${id}`), session(createdAt, { id }));
  }
  batch.set(admin.doc('accountDeletionRequests/deleting'), { state: 'pending' });
  batch.set(admin.doc('users/deleting/practiceRecords/session_one'), session(createdAt));
  await batch.commit();
  assert.equal(await redactSpeechPage(admin, 'practiceRecords', { FieldValue, FieldPath }, now), true);
  assert.equal(await redactSpeechPage(admin, 'practiceRecords', { FieldValue, FieldPath }, now), false);
  for (const row of (await admin.collection('users/owner/practiceRecords').get()).docs) assert.equal(row.get('transcription'), '');
  assert.equal((await admin.doc('users/deleting/backupChanges/practice_session_one').get()).exists, false);
  assert.deepEqual((await admin.doc('privacyMaintenance/practiceRecords').get()).data(), {});
});
