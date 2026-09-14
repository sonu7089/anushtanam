const { before, after, beforeEach, test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc, writeBatch, runTransaction, serverTimestamp, Timestamp } = require('firebase/firestore');

let env;
const token = 'a'.repeat(64);
const fresh = 'b'.repeat(64);
const user = (uid, provider = 'google.com', authTime = Math.floor(Date.now() / 1000)) => env.authenticatedContext(uid, {
  email: uid === 'child' ? 'c123456789012@child.anushtanam.app' : `${uid}@example.test`,
  auth_time: authTime, firebase: { sign_in_provider: provider }
}).firestore();
const link = (parent = 'parent', method = 'QR') => ({
  parentUid: parent, childUid: 'child', parentName: parent, childName: 'Aarav',
  status: 'APPROVED', method, ...(method === 'QR' ? { inviteToken: token } : {}),
  createdAt: serverTimestamp(), updatedAt: serverTimestamp()
});

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-anushtanam',
    firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } });
});
after(async () => env?.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    await Promise.all([
      setDoc(doc(db, 'users/child'), { role: 'CHILD', displayName: 'Aarav', linkCode: '123456789012' }),
      ...['parent', 'parent2'].map(uid => setDoc(doc(db, `users/${uid}`), { role: 'PARENT', displayName: uid })),
      setDoc(doc(db, 'users/individual'), { role: 'INDIVIDUAL', displayName: 'Meera' }),
      setDoc(doc(db, 'users/child2'), { role: 'CHILD', displayName: 'Tara' }),
      setDoc(doc(db, 'progress/child'), { correctSessions: 8 })
    ]);
  });
});
async function invite(value = token) {
  const db = user('child');
  const batch = writeBatch(db);
  batch.set(doc(db, `familyInvites/${value}`), {
    childUid: 'child', childName: 'Aarav', createdAt: serverTimestamp(), usedBy: ''
  });
  batch.set(doc(db, 'familyInviteState/child'), { token: value });
  return batch.commit();
}
async function redeem(parent = 'parent', value = token) {
  const db = user(parent);
  return runTransaction(db, async tx => {
    const ref = doc(db, `familyInvites/${value}`);
    await tx.get(ref);
    await tx.get(doc(db, `links/${parent}_child`));
    tx.update(ref, { usedBy: parent, usedAt: serverTimestamp() });
    tx.set(doc(db, `links/${parent}_child`), { ...link(parent), inviteToken: value });
  });
}

test('child creates an invitation and parent redeems it without a pending request', async () => {
  await assertFails(getDoc(doc(user('parent'), 'progress/child')));
  await assertSucceeds(invite());
  await assertSucceeds(redeem());
  assert.equal((await getDoc(doc(user('parent'), 'links/parent_child'))).data().status, 'APPROVED');
  await assertSucceeds(getDoc(doc(user('parent'), 'progress/child')));
});
test('a second parent cannot reuse a redeemed QR code', async () => {
  await invite(); await redeem(); await assertFails(redeem('parent2'));
});
test('two simultaneous scans allow only one parent to redeem', async () => {
  await invite();
  const results = await Promise.allSettled([redeem(), redeem('parent2')]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
});
test('expired QR is rejected using server time', async () => {
  await invite();
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), `familyInvites/${token}`), {
    createdAt: Timestamp.fromMillis(Date.now() - 6 * 60_000)
  }));
  await assertFails(redeem());
});
test('making a new QR revokes the older code', async () => {
  await invite(); await invite(fresh);
  await assertFails(redeem()); await assertSucceeds(redeem('parent', fresh));
});
test('old tokens cannot be reactivated or deleted and recreated by a client', async () => {
  await invite(); await invite(fresh);
  await assertFails(updateDoc(doc(user('child'), 'familyInviteState/child'), { token }));
  await assertFails(deleteDoc(doc(user('child'), `familyInvites/${token}`)));
  await assertFails(updateDoc(doc(user('child'), `familyInvites/${token}`), { createdAt: serverTimestamp() }));
});
test('parent cannot discover tokens by listing invitations or reading child state', async () => {
  await invite();
  await assertFails(getDocs(collection(user('parent'), 'familyInvites')));
  await assertFails(getDoc(doc(user('parent'), 'familyInviteState/child')));
  await assertFails(getDocs(collection(user('parent'), 'familyInviteState')));
});
test('a forged approved link without QR redemption is rejected', async () => {
  await invite(); await assertFails(setDoc(doc(user('parent'), 'links/parent_child'), link()));
});
test('consuming a QR without creating its link is rejected', async () => {
  await invite();
  await assertFails(updateDoc(doc(user('parent'), `familyInvites/${token}`), {
    usedBy: 'parent', usedAt: serverTimestamp()
  }));
});
test('one child cannot create another child invitation', async () => {
  const db = user('child2');
  const batch = writeBatch(db);
  batch.set(doc(db, `familyInvites/${token}`), { childUid: 'child', childName: 'Aarav', createdAt: serverTimestamp(), usedBy: '' });
  batch.set(doc(db, 'familyInviteState/child'), { token });
  await assertFails(batch.commit());
});
test('individual and signed-out accounts cannot redeem invitations', async () => {
  await invite();
  await assertFails(redeem('individual'));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `familyInvites/${token}`)));
});
test('child password session cannot create a PIN link or access child data', async () => {
  await assertFails(setDoc(doc(user('child', 'password'), 'links/parent_child'), link('parent', 'PIN')));
  await assertFails(getDoc(doc(user('child', 'password'), 'users/child')));
  await assertFails(getDoc(doc(user('parent'), 'progress/child')));
  await assertFails(getDoc(doc(user('parent2'), 'progress/child')));
});
test('parent cannot claim that a PIN was verified', async () => {
  await assertFails(setDoc(doc(user('parent'), 'links/parent_child'), link('parent', 'PIN')));
});
test('a Google-only child session cannot impersonate password verification', async () => {
  await assertFails(setDoc(doc(user('child'), 'links/parent_child'), link('parent', 'PIN')));
});
test('a different child cannot approve a PIN link', async () => {
  await assertFails(setDoc(doc(user('child2', 'password'), 'links/parent_child'), link('parent', 'PIN')));
});
test('existing pending requests can still be approved or rejected by the child', async () => {
  const pending = { ...link('parent', 'PIN'), status: 'PENDING', childName: '' };
  delete pending.method;
  await assertSucceeds(setDoc(doc(user('parent'), 'links/parent_child'), pending));
  await assertSucceeds(updateDoc(doc(user('child'), 'links/parent_child'), { status: 'REJECTED', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(user('child'), 'links/parent_child'), { status: 'APPROVED', childName: 'Aarav', updatedAt: serverTimestamp() }));
});
test('QR can approve an existing pending request without replacing its creation time', async () => {
  const pending = { ...link(), status: 'PENDING', childName: '' };
  delete pending.method; delete pending.inviteToken;
  const db = user('parent');
  await setDoc(doc(db, 'links/parent_child'), pending);
  const createdAt = (await getDoc(doc(db, 'links/parent_child'))).data().createdAt;
  await invite();
  const batch = writeBatch(db);
  batch.update(doc(db, `familyInvites/${token}`), { usedBy: 'parent', usedAt: serverTimestamp() });
  batch.set(doc(db, 'links/parent_child'), { ...link(), createdAt });
  await assertSucceeds(batch.commit());
});
test('a Google account cannot change from parent to child or change an existing child ID', async () => {
  await assertFails(updateDoc(doc(user('parent'), 'users/parent'), { role: 'CHILD' }));
  await assertFails(updateDoc(doc(user('child'), 'users/child'), { linkCode: '999999999999' }));
});
test('retired child IDs cannot be created, read or enumerated', async () => {
  await assertFails(setDoc(doc(user('child'), 'childCodes/234567890123'), { uid: 'child' }));
  await assertFails(getDoc(doc(user('parent'), 'childCodes/234567890123')));
  await assertFails(getDocs(collection(user('parent'), 'childCodes')));
});

test('only a Google session can create a child profile', async () => {
  await assertFails(setDoc(doc(user('new-child', 'password'), 'users/new-child'), { role: 'CHILD', displayName: 'Tara', createdAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(user('new-child'), 'users/new-child'), { role: 'CHILD', displayName: 'Tara', createdAt: serverTimestamp() }));
  await assertFails(deleteDoc(doc(user('new-child'), 'users/new-child')));
});

test('stale or missing authentication time cannot issue an invitation', async () => {
  for (const time of [Math.floor(Date.now() / 1000) - 600, null, Math.floor(Date.now() / 1000) + 600]) {
    const db = user('child', 'google.com', time);
    const batch = writeBatch(db);
    batch.set(doc(db, `familyInvites/${token}`), { childUid: 'child', childName: 'Aarav', createdAt: serverTimestamp(), usedBy: '' });
    batch.set(doc(db, 'familyInviteState/child'), { token });
    await assertFails(batch.commit());
  }
});

test('stale child session cannot approve a pending request', async () => {
  const pending = { ...link('parent', 'PIN'), status: 'PENDING', childName: '' };
  delete pending.method;
  await setDoc(doc(user('parent'), 'links/parent_child'), pending);
  await assertFails(updateDoc(doc(user('child', 'google.com', Math.floor(Date.now() / 1000) - 600), 'links/parent_child'), {
    status: 'APPROVED', childName: 'Aarav', updatedAt: serverTimestamp()
  }));
});

test('japa entries are private and corrections cannot move them to another goal', async () => {
  const fixtures = require('./backup-fixtures.cjs');
  const owner = user('individual');
  const path = 'users/individual/japaEntries/entry-one';
  const entry = fixtures.entry({ targetId: 'goal-one' });
  const publish = value => fixtures.publish(owner, 'individual', 'entry', value);
  await assertSucceeds(publish(entry));
  await assertFails(getDoc(doc(user('parent'), path)));
  await assertFails(setDoc(doc(user('child'), path), entry));
  const edited = { ...entry, quantity: 500, revision: 2, operationId: 'edit-one', updatedAtMillis: 20 };
  await assertSucceeds(publish(edited));
  await assertFails(publish({ ...edited, quantity: 600, revision: 1 }));
  await assertFails(publish({ ...edited, targetId: 'later-goal', revision: 3 }));
  await assertFails(publish({ ...edited, quantity: -1, revision: 3 }));
  await assertFails(deleteDoc(doc(owner, path)));
  assert.equal((await getDoc(doc(owner, path))).data().quantity, 500);
});

test('a japa goal baseline is established once and history cannot be erased', async () => {
  const fixtures = require('./backup-fixtures.cjs');
  const owner = user('individual');
  const ref = doc(owner, 'users/individual/japaGoals/goal-one');
  const goal = fixtures.goal();
  await assertSucceeds(fixtures.publish(owner, 'individual', 'goal', goal));
  await assertFails(fixtures.publish(owner, 'individual', 'goal', { ...goal, baselineCount: 64 }));
  await assertSucceeds(fixtures.publish(owner, 'individual', 'goal', { ...goal, endedAtMillis: 100 }));
  await assertFails(getDoc(doc(user('parent'), 'users/individual/japaGoals/goal-one')));
  await assertFails(deleteDoc(ref));
});
