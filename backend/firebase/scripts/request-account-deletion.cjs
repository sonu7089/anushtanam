// For identity-verified email requests. Dry run unless --execute is supplied.
const { createRequire } = require('node:module');
const requireFunctions = createRequire(require.resolve('../functions/package.json'));
const { initializeApp } = requireFunctions('firebase-admin/app');
const { getAuth } = requireFunctions('firebase-admin/auth');
const { getFirestore, FieldValue } = requireFunctions('firebase-admin/firestore');

async function main() {
  const [projectId, uid, flag] = process.argv.slice(2);
  if (!projectId || !uid || uid.includes('/') || ![undefined, '--execute'].includes(flag) || process.argv.length > 5) {
    throw new Error('Usage: node scripts/request-account-deletion.cjs PROJECT_ID VERIFIED_UID [--execute]');
  }
  initializeApp({ projectId });
  const request = getFirestore().collection('accountDeletionRequests').doc(uid);
  if ((await request.get()).exists) { console.log('Deletion already requested. The backend will resume it.'); return; }
  await getAuth().getUser(uid);
  if (flag !== '--execute') { console.log('Account exists. After verifying the requester, use --execute to enqueue erasure.'); return; }
  await request.create({ state: 'pending', requestedAt: FieldValue.serverTimestamp() });
  console.log('Deletion requested. Verify backend completion before closing the email request.');
}
main().catch(() => { console.error('Could not enqueue deletion. Check project, verified UID, credentials and connectivity.'); process.exitCode = 1; });
