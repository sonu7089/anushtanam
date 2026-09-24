"use strict";

const RETENTION_MS = 365 * 24 * 60 * 60 * 1000;

// Compatibility exports are intentionally non-destructive after full-history sync.
function redactSpeech(value) { return value; }

async function eraseAccount(db, auth, uid, { FieldValue, Timestamp }) {
  const request = db.collection("accountDeletionRequests").doc(uid);
  const state = (await request.get()).data();
  if (!state || state.state !== "pending") return;
  // Disable refresh before removing data. A missing Auth user is a successful retry.
  try { await auth.updateUser(uid, { disabled: true }); await auth.revokeRefreshTokens(uid); }
  catch (error) { if (error.code !== "auth/user-not-found") throw error; }

  async function removeMatches(collection, field) {
    for (;;) {
      const page = await db.collection(collection).where(field, "==", uid).limit(100).get();
      if (page.empty) return;
      for (const row of page.docs) {
        if (collection === "messageInstallations") {
          await db.recursiveDelete(db.collection("messageTestDevices").doc(row.id));
        }
        await db.recursiveDelete(row.ref);
      }
    }
  }
  for (const [collection, fields] of [
    ["links", ["parentUid", "childUid"]], ["goals", ["parentUid", "childUid"]],
    ["familyInvites", ["childUid", "usedBy"]], ["childCodes", ["uid", "childUid"]],
    ["messageInstallations", ["uid"]], ["messageTestDevices", ["uid"]]
  ]) for (const field of fields) await removeMatches(collection, field);
  for (const collection of ["progress", "selfTargets", "purashcharana", "familyInviteState", "users"]) {
    await db.recursiveDelete(db.collection(collection).doc(uid));
  }
  // Includes nested revisions, private tokens, conflicts, repair archives and feeds.
  // A linked account is a separate identity; never cascade into it.
  try { await auth.deleteUser(uid); }
  catch (error) { if (error.code !== "auth/user-not-found") throw error; }
  await request.update({ state: "complete", completedAt: FieldValue.serverTimestamp(),
    // Keep the barrier until all pre-deletion one-hour ID tokens have expired.
    purgeAfter: Timestamp.fromMillis(Date.now() + 2 * 60 * 60 * 1000) });
}

async function redactSpeechPage() { return false; }

module.exports = { RETENTION_MS, redactSpeech, eraseAccount, redactSpeechPage };
