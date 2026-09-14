/*
 * Anushtanam — optional FCM push on new goal (Phase B).
 *
 * REQUIRES THE BLAZE (pay-as-you-go) PLAN. Deploy with:
 *   firebase deploy --only functions
 *
 * When a parent creates a goal document, this trigger reads the child's stored FCM
 * token (users/{childUid}/private/messaging.fcmToken) and sends a push so the child is notified even
 * when the app is closed. Without Blaze, the app's same-device Firestore listener
 * still surfaces new goals while the child has the app open — this function only adds
 * instant background push.
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
Object.assign(exports, require("./messages"));

exports.onGoalCreated = onDocumentCreated({ document: "goals/{goalId}", retry: false }, async (event) => {
  const snap = event.data;
  if (!snap) return;
  const goal = snap.data();
  const childUid = goal.childUid;
  if (!childUid) return;

  const userDoc = await getFirestore().collection("users").doc(childUid).collection("private").doc("messaging").get();
  const token = userDoc.exists ? userDoc.get("fcmToken") : null;
  if (!token) {
    console.log("No FCM token; skipping goal push.");
    return;
  }

  // Free text and practice details belong in-app, not on the lock screen.
  const body = "Open Anushtanam to view your new goal.";

  try {
    await getMessaging().send({
      token,
      notification: {
        title: "New goal from your parent",
        body,
      },
      data: {
        title: "New goal from your parent",
        body,
      },
      android: { priority: "high" },
    });

  } catch (error) {
    // Permanent registration errors cannot be repaired by retrying the goal event.
    if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(error.code)) {
      console.warn("Goal push skipped: invalid registration.");
      return;
    }
    console.error("Goal push failed", { type: error?.name || "UnknownError" });
    throw error;
  }
});
