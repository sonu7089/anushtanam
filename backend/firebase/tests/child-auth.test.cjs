const { test } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword,
  signInWithCredential, linkWithCredential, GoogleAuthProvider, EmailAuthProvider, signOut } = require('firebase/auth');

function authFor(name) {
  const app = initializeApp({ apiKey: 'fake-api-key', projectId: 'demo-anushtanam' }, name);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  return { app, auth };
}
const google = sub => GoogleAuthProvider.credential(JSON.stringify({ sub, email: `${sub}@example.test`, email_verified: true }));

test('existing child keeps both Google and ID/PIN sign-in on the same UID', async () => {
  const { app, auth } = authFor('existing-child');
  try {
    const email = 'c345678901234@child.anushtanam.app';
    const uid = (await createUserWithEmailAndPassword(auth, email, '123456')).user.uid;
    await linkWithCredential(auth.currentUser, google('existing-child-google'));
    await signOut(auth);
    assert.equal((await signInWithEmailAndPassword(auth, email, '123456')).user.uid, uid);
    await signOut(auth);
    assert.equal((await signInWithCredential(auth, google('existing-child-google'))).user.uid, uid);
    await signOut(auth);
    assert.equal((await signInWithEmailAndPassword(auth, email, '123456')).user.uid, uid);
  } finally { await deleteApp(app); }
});
test('Google-first child can add ID/PIN and return through either method', async () => {
  const { app, auth } = authFor('google-first-child');
  try {
    const email = 'c456789012345@child.anushtanam.app';
    const uid = (await signInWithCredential(auth, google('new-child-google'))).user.uid;
    await linkWithCredential(auth.currentUser, EmailAuthProvider.credential(email, '654321'));
    await signOut(auth);
    assert.equal((await signInWithEmailAndPassword(auth, email, '654321')).user.uid, uid);
    await signOut(auth);
    assert.equal((await signInWithCredential(auth, google('new-child-google'))).user.uid, uid);
    await signOut(auth);
    assert.equal((await signInWithEmailAndPassword(auth, email, '654321')).user.uid, uid);
  } finally { await deleteApp(app); }
});
test('verifying a child PIN uses a separate session and never signs the parent out', async () => {
  const parent = authFor('parent-session');
  const child = authFor('pin-session');
  try {
    const parentUid = (await signInWithCredential(parent.auth, google('parent-google'))).user.uid;
    const email = 'c567890123456@child.anushtanam.app';
    const childUid = (await createUserWithEmailAndPassword(child.auth, email, '345678')).user.uid;
    await signOut(child.auth);
    await assert.rejects(signInWithEmailAndPassword(child.auth, email, '000000'));
    assert.equal(parent.auth.currentUser.uid, parentUid);
    assert.equal((await signInWithEmailAndPassword(child.auth, email, '345678')).user.uid, childUid);
    await signOut(child.auth);
    assert.equal(parent.auth.currentUser.uid, parentUid);
    assert.equal(child.auth.currentUser, null);
  } finally { await Promise.all([deleteApp(parent.app), deleteApp(child.app)]); }
});

test('a child cannot link a Google account already owned by a parent', async () => {
  const parent = authFor('collision-parent');
  const child = authFor('collision-child');
  try {
    const credential = google('already-a-parent');
    const parentUid = (await signInWithCredential(parent.auth, credential)).user.uid;
    const childUid = (await createUserWithEmailAndPassword(child.auth,
      'c678901234567@child.anushtanam.app', '456789')).user.uid;
    await assert.rejects(linkWithCredential(child.auth.currentUser, credential));
    assert.equal(parent.auth.currentUser.uid, parentUid);
    assert.equal(child.auth.currentUser.uid, childUid);
  } finally { await Promise.all([deleteApp(parent.app), deleteApp(child.app)]); }
});
