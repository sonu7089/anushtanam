"use strict";

const RETENTION_MS = 365 * 24 * 60 * 60 * 1000;

function redactSpeech(value, now = Date.now()) {
  if (value?.type !== "session" || !Number.isSafeInteger(value.createdAt) ||
      value.createdAt > now - RETENTION_MS) return value;
  return { ...value, transcription: "", wordScoresJson: "" };
}

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

async function redactSpeechPage(db, group, { FieldValue, FieldPath }, now = Date.now()) {
  const cursor = db.collection("privacyMaintenance").doc(group);
  const previous = (await cursor.get()).data();
  const field = group === "practiceRecords" ? "createdAt" : "value.createdAt";
  let query = db.collectionGroup(group).where(field, "<=", now - RETENTION_MS)
    .orderBy(field).orderBy(FieldPath.documentId()).limit(200);
  if (previous?.path) query = query.startAfter(previous.createdAt, db.doc(previous.path));
  const page = await query.get();
  for (const row of page.docs) {
    // Restrict collection-group matches to the canonical user hierarchy.
    if (!/^users\/[^/]+\/(practiceRecords|backupChanges)\/[^/]+$/.test(row.ref.path)) continue;
    await db.runTransaction(async tx => {
      const fresh = await tx.get(row.ref);
      const data = fresh.data();
      if (!data) return;
      const uid = row.ref.path.split("/")[1];
      if ((await tx.get(db.collection("accountDeletionRequests").doc(uid))).exists) return;
      const value = group === "practiceRecords" ? data : data.value;
      const redacted = redactSpeech(value, now);
      if (redacted === value || (value.transcription === "" && value.wordScoresJson === "")) return;
      if (group === "practiceRecords") {
        tx.set(row.ref, redacted);
        tx.set(row.ref.parent.parent.collection("backupChanges").doc(`practice_${row.id}`), {
          type: "practice", id: row.id, value: redacted, changedAt: FieldValue.serverTimestamp()
        });
      } else {
        tx.update(row.ref, { value: redacted, changedAt: FieldValue.serverTimestamp() });
      }
    });
  }
  const last = page.docs.at(-1);
  await cursor.set(page.size === 200 ? { path: last.ref.path, createdAt: last.get(field) } : {});
  return page.size === 200;
}

module.exports = { RETENTION_MS, redactSpeech, eraseAccount, redactSpeechPage };
