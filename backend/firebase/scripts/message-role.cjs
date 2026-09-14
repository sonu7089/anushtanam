// Uses application-default credentials. Dry-run unless --apply is supplied.
const { createRequire } = require('node:module');
const requireFunctions = createRequire(require('node:path').resolve(__dirname, '../functions/package.json'));
const { initializeApp } = requireFunctions('firebase-admin/app');
const { getAuth } = requireFunctions('firebase-admin/auth');
const { getFirestore } = requireFunctions('firebase-admin/firestore');

async function main() {
  const [projectId, uid, role, operator, ...flags] = process.argv.slice(2);
  if (!projectId || !uid || !['editor', 'publisher', 'none', 'inspect'].includes(role) ||
      !operator || flags.some(flag => flag !== '--apply')) {
    throw new Error('Usage: node scripts/message-role.cjs PROJECT_ID UID editor|publisher|none|inspect OPERATOR [--apply]');
  }
  initializeApp({ projectId });
  const auth = getAuth();
  const user = await auth.getUser(uid);
  const previousRole = user.customClaims?.messageRole || null;
  const mfaEnrolled = Boolean(user.multiFactor?.enrolledFactors?.length);
  console.log(JSON.stringify({ projectId, uid, previousRole, requestedRole: role, mfaEnrolled,
    apply: flags.includes('--apply') && role !== 'inspect' }));
  if (role === 'inspect') return;
  if (role !== 'none' && (user.disabled || !mfaEnrolled)) {
    throw new Error('Grants require an enabled account with Firebase MFA enrolled.');
  }
  if (!flags.includes('--apply')) return;
  // Persist intent before changing Auth; Auth and Firestore cannot commit atomically.
  const audit = getFirestore().collection('messageAudit').doc();
  await audit.set({ uid: operator, action: 'messageRoleChange', target: uid,
    previousRole, role, at: Date.now(), state: 'requested' });
  const claims = { ...user.customClaims };
  delete claims.messageRole;
  if (role !== 'none') claims.messageRole = role;
  await auth.setCustomUserClaims(uid, claims);
  await auth.revokeRefreshTokens(uid);
  await audit.update({ state: 'complete', completedAt: Date.now() });
  console.log('Role updated and refresh tokens revoked. Existing ID tokens may last up to one hour.');
}
main().catch(() => {
  console.error('Role operation failed. Check arguments, MFA enrollment, credentials and the audit record; inspect the user before retrying.');
  process.exitCode = 1;
});
