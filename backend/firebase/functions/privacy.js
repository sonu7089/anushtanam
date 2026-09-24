"use strict";

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { eraseAccount } = require("./privacy-service");

// Rules authenticate the request and require a fresh sign-in. The document is also
// a barrier against old ID tokens and concurrent sync while erasure is running.
exports.eraseRequestedAccount = onDocumentCreated({ document: "accountDeletionRequests/{uid}",
  region: "us-central1", retry: true, timeoutSeconds: 540, maxInstances: 2 }, async event => {
  await eraseAccount(getFirestore(), getAuth(), event.params.uid, { FieldValue, Timestamp });
});

exports.maintainPrivacy = onSchedule({ schedule: "every 60 minutes", region: "us-central1",
  timeoutSeconds: 540, maxInstances: 1 }, async () => {
  const db = getFirestore();
  // Resume failed erasures even if the event retry window has elapsed.
  const pending = await db.collection("accountDeletionRequests").where("state", "==", "pending").limit(10).get();
  let failed = false;
  for (const request of pending.docs) {
    try { await eraseAccount(db, getAuth(), request.id, { FieldValue, Timestamp }); }
    catch (_) { failed = true; console.error("Account erasure requires retry"); }
  }
  // Full practice details remain available for account restore. No age-based redaction.
  const completed = await db.collection("accountDeletionRequests").where("purgeAfter", "<=", Timestamp.now()).limit(100).get();
  for (const request of completed.docs) await request.ref.delete();
  if (failed) throw new Error("Account erasure requires retry");
});
