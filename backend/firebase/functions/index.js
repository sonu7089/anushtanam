/*
 * Anushtanam — optional FCM push on new goal (Phase B).
 *
 * REQUIRES THE BLAZE (pay-as-you-go) PLAN. Deploy with:
 *   firebase deploy --only functions
 *
 * When a parent creates a goal document, this trigger reads the child's stored FCM
 * token (users/{childUid}.fcmToken) and sends a push so the child is notified even
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

const REWARD_EMOJI = {
  MONEY: "\u{1F4B0}",
  FOOD: "\u{1F37D}\u{FE0F}",
  GIFT: "\u{1F381}",
  OUTING: "\u{1F3A1}",
  TOY: "\u{1F9F8}",
  OTHER: "\u{2B50}",
};

exports.onGoalCreated = onDocumentCreated("goals/{goalId}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  const goal = snap.data();
  const childUid = goal.childUid;
  if (!childUid) return;

  const userDoc = await getFirestore().collection("users").doc(childUid).get();
  const token = userDoc.exists ? userDoc.get("fcmToken") : null;
  if (!token) {
    console.log(`No FCM token for child ${childUid}; skipping push.`);
    return;
  }

  const count = goal.targetRecitationCount || 0;
  const emoji = REWARD_EMOJI[goal.rewardType] || REWARD_EMOJI.OTHER;
  const reward = goal.rewardDescription
    ? `${emoji} ${goal.rewardDescription}`
    : "a reward";
  const body = `Recite the Gayatri Mantra ${count} times to earn ${reward}.`;

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

  console.log(`Sent goal push to child ${childUid}.`);
});
