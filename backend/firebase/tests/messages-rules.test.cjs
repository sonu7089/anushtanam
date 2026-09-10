const { before, after, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, query, orderBy, limit, startAfter, updateDoc, serverTimestamp } = require('firebase/firestore');
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-anushtanam-messages',
    firestore: { rules: fs.readFileSync('firestore.rules', 'utf8') } });
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    for (const name of ['publishedContent', 'contentDrafts', 'campaigns', 'messageAudit', 'messageTestDevices', 'messagePublicationJobs']) {
      await setDoc(doc(ctx.firestore(), `${name}/one`), { title: 'A note', publishedAt: 10, pinned: false });
    }
  });
});
after(async () => env?.cleanup());

test('the exact paginated feed is public, while drafts and sending data are private', async () => {
  const db = env.unauthenticatedContext().firestore();
  const first = await assertSucceeds(getDocs(query(collection(db, 'publishedContent'), orderBy('pinned', 'desc'), orderBy('publishedAt', 'desc'), limit(30))));
  await assertSucceeds(getDocs(query(collection(db, 'publishedContent'), orderBy('pinned', 'desc'), orderBy('publishedAt', 'desc'), startAfter(first.docs[0]), limit(30))));
  await assertSucceeds(getDoc(doc(db, 'publishedContent/one')));
  for (const context of [env.unauthenticatedContext(), env.authenticatedContext('parent', { role: 'PARENT' }),
    env.authenticatedContext('publisher', { messageRole: 'publisher' })]) {
    for (const name of ['contentDrafts', 'campaigns', 'messageAudit', 'messageTestDevices', 'messagePublicationJobs']) {
      await assertFails(getDoc(doc(context.firestore(), `${name}/one`)));
    }
    await assertFails(setDoc(doc(context.firestore(), 'publishedContent/one'), { title: 'Changed' }));
  }
});
test('a phone registration stays with its owner and cannot grant itself test access', async () => {
  const owner = env.authenticatedContext('reader').firestore();
  const other = env.authenticatedContext('other').firestore();
  const data = { uid: 'reader', token: 'sample-token', enabled: true, schemaVersion: 1,
    locale: 'en', appVersion: 1, updatedAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(owner, 'messageInstallations/reader_phone'), data));
  await assertFails(getDoc(doc(other, 'messageInstallations/reader_phone')));
  await assertFails(updateDoc(doc(other, 'messageInstallations/reader_phone'), { uid: 'other', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(owner, 'messageInstallations/reader_phone'), { test: true, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(owner, 'messageTestDevices/reader_phone'), { label: 'Me' }));
  await assertSucceeds(updateDoc(doc(owner, 'messageInstallations/reader_phone'), { enabled: false, updatedAt: serverTimestamp() }));
});
