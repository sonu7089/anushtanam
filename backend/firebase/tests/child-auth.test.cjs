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

test('Google child returns to the same UID with no password provider', async () => {
  const { app, auth } = authFor('google-only-child');
  try {
    const credential = google('google-only-child');
    const uid = (await signInWithCredential(auth, credential)).user.uid;
    assert.deepEqual(auth.currentUser.providerData.map(p => p.providerId), ['google.com']);
    await signOut(auth);
    assert.equal((await signInWithCredential(auth, credential)).user.uid, uid);
    assert.deepEqual(auth.currentUser.providerData.map(p => p.providerId), ['google.com']);
  } finally { await deleteApp(app); }
});
