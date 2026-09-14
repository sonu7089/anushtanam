// Run with application-default credentials: node scripts/migrate-private-tokens.cjs PROJECT_ID [--apply]
// Dry-run by default. Never log token values. Each move is atomic and restartable.
const { createRequire } = require('node:module');
const requireFunctions = createRequire(require('node:path').resolve(__dirname, '../functions/package.json'));
const { initializeApp } = requireFunctions('firebase-admin/app');
const { getFirestore, FieldValue, FieldPath } = requireFunctions('firebase-admin/firestore');

async function main() {
  const [projectId, ...flags] = process.argv.slice(2);
  if (!projectId || projectId.startsWith('-') || flags.some(flag => !['--apply', '--firebase-cli'].includes(flag)))
    throw new Error('Usage: node scripts/migrate-private-tokens.cjs PROJECT_ID [--apply] [--firebase-cli]');
  const mode = flags.includes('--apply') ? '--apply' : undefined;
  let db;
  if (flags.includes('--firebase-cli')) {
    const auth = require('firebase-tools/lib/auth');
    const api = require('firebase-tools/lib/api');
    const account = auth.getProjectDefaultAccount(require('node:path').resolve(__dirname, '..'));
    if (!account) throw new Error('Sign in with firebase login first');
    const { OAuth2Client } = requireFunctions('google-auth-library');
    const authClient = new OAuth2Client(api.clientId(), api.clientSecret());
    authClient.setCredentials({ refresh_token: account.tokens.refresh_token });
    const { Firestore } = requireFunctions('@google-cloud/firestore');
    db = new Firestore({ projectId, authClient });
  } else {
    initializeApp({ projectId });
    db = getFirestore();
  }
  let cursor, scanned = 0, found = 0, moved = 0;
  while (true) {
    let query = db.collection('users').orderBy(FieldPath.documentId()).limit(200);
    if (cursor) query = query.startAfter(cursor);
    const page = await query.get();
    if (page.empty) break;
    for (const profile of page.docs) {
      scanned++;
      if (!Object.hasOwn(profile.data(), 'fcmToken')) continue;
      found++;
      if (mode !== '--apply') continue;
      const changed = await db.runTransaction(async tx => {
        const current = await tx.get(profile.ref);
        if (!current.exists || !Object.hasOwn(current.data(), 'fcmToken')) return false;
        const ref = profile.ref.collection('private').doc('messaging');
        const privateDoc = await tx.get(ref);
        const valid = value => typeof value === 'string' && value.length > 0 && value.length <= 4096;
        const token = current.get('fcmToken');
        // A token already registered by the new client is fresher than the legacy copy.
        if (!valid(privateDoc.get('fcmToken')) && valid(token)) tx.set(ref, { fcmToken: token });
        tx.update(profile.ref, { fcmToken: FieldValue.delete() });
        return true;
      });
      if (changed) moved++;
    }
    cursor = page.docs.at(-1);
  }
  console.log(JSON.stringify({ projectId, apply: mode === '--apply', scanned, found, moved }));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
