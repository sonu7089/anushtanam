"use strict";

// The website uses this callable API. It must never write production documents directly.
const { defineBoolean } = require("firebase-functions/params");
const enforceAdminAppCheck = defineBoolean("ENFORCE_ADMIN_APP_CHECK", { default: false });
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { getFirestore, FieldValue, FieldPath } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const policy = require("./message-policy");
const db = () => getFirestore();
const options = { region: "us-central1", maxInstances: 3, timeoutSeconds: 60 };

function authorize(request, publishing = false) {
  const role = request.auth?.token?.messageRole;
  if (!request.auth || !(publishing ? ["publisher"] : ["editor", "publisher"]).includes(role)) {
    throw new HttpsError("permission-denied", "Your account cannot do that.");
  }
  return request.auth.uid;
}
// Shared across instances. One budget per uid prevents bypass by switching operations.
// Audience estimates consume ten credits; other calls consume one (60 credits/minute).
async function throttle(uid, op, now) {
  const key = require("node:crypto").createHash("sha256").update(uid).digest("hex");
  const bucket = db().collection("messageRateLimits").doc(key);
  await db().runTransaction(async tx => {
    const prior = (await tx.get(bucket)).data();
    const tokens = prior ? Math.min(60, prior.tokens + Math.max(0, now - prior.at) / 1000) : 60;
    const cost = op === "estimateAudience" ? 10 : 1;
    if (tokens < cost) throw new HttpsError("resource-exhausted", "Too many actions. Wait a moment and try again.");
    tx.set(bucket, { tokens: tokens - cost, at: Math.max(prior?.at || 0, now) });
  });
}
function ref(collection, id) { return db().collection(collection).doc(policy.id(id)); }
function audit(tx, uid, action, target) {
  tx.set(db().collection("messageAudit").doc(), { uid, action, target, at: Date.now() });
}
async function revision(contentId, revisionNumber) {
  if (!Number.isSafeInteger(revisionNumber) || revisionNumber < 1) throw new policy.PolicyError("Choose a saved revision.");
  const snapshot = await ref("contentDrafts", contentId).collection("revisions").doc(String(revisionNumber)).get();
  if (!snapshot.exists) throw new HttpsError("not-found", "That revision was not found.");
  return snapshot.data();
}

exports.adminMessages = onCall({ ...options, enforceAppCheck: enforceAdminAppCheck }, async request => {
  try {
    const input = request.data || {};
    const uid = authorize(request);
    const now = Date.now();
    await throttle(uid, input.op, now);
    switch (input.op) {
      case "saveDraft": {
        const draftRef = ref("contentDrafts", input.contentId);
        const fields = policy.content(input.content);
        return await db().runTransaction(async tx => {
          const previous = await tx.get(draftRef);
          const version = (previous.data()?.revision || 0) + 1;
          if ((previous.data()?.revision || 0) !== input.expectedRevision) {
            throw new HttpsError("aborted", "Someone has changed this draft. Load it again before saving.");
          }
          const saved = { ...fields, contentId: input.contentId, revision: version, editor: uid, editedAt: now };
          tx.set(draftRef, saved);
          tx.create(draftRef.collection("revisions").doc(String(version)), saved);
          audit(tx, uid, "saveDraft", input.contentId);
          return { contentId: input.contentId, revision: version };
        });
      }
      case "getContent": {
        const draft = await ref("contentDrafts", input.contentId).get();
        const revisions = await draft.ref.collection("revisions").orderBy("revision", "desc").limit(50).get();
        const published = await ref("publishedContent", input.contentId).get();
        const scheduled = await ref("messagePublicationJobs", input.contentId).get();
        return { draft: draft.data() || null, revisions: revisions.docs.map(d => d.data()),
          published: published.data() || null, scheduled: scheduled.data() || null };
      }
      case "listContent": {
        let query = db().collection("contentDrafts").orderBy(FieldPath.documentId()).limit(50);
        if (input.after) query = query.startAfter(policy.id(input.after));
        const rows = await query.get();
        return { items: rows.docs.map(d => d.data()), next: rows.size === 50 ? rows.docs.at(-1).id : null };
      }
      case "publish": {
        authorize(request, true);
        policy.window(input.startsAt, input.expiresAt, now);
        const content = await revision(input.contentId, input.revision);
        const timezone = policy.timezone(input.timezone);
        // Validate the IANA timezone. Dates remain UTC milliseconds at the API boundary.
        const published = { ...policy.content(content), contentId: input.contentId, revision: input.revision,
          published: true, publishedAt: input.startsAt, expiresAt: input.expiresAt };
        await db().runTransaction(async tx => {
          const job = ref("messagePublicationJobs", input.contentId);
          if (input.startsAt > now) tx.set(job, { projection: published, dueAt: input.startsAt, timezone, uid });
          else { tx.set(ref("publishedContent", input.contentId), published); tx.delete(job); }
          audit(tx, uid, input.startsAt > now ? "schedulePublish" : "publish", input.contentId);
        });
        return { scheduled: input.startsAt > now, contentId: input.contentId };
      }
      case "unpublish":
      case "cancelPublication": {
        authorize(request, true);
        await db().runTransaction(async tx => {
          tx.delete(ref("messagePublicationJobs", input.contentId));
          if (input.op === "unpublish") tx.delete(ref("publishedContent", input.contentId));
          audit(tx, uid, input.op, input.contentId);
        });
        return { ok: true };
      }
      case "estimateAudience": {
        authorize(request, true);
        if (!["all", "test"].includes(input.audience)) throw new policy.PolicyError("Choose an audience.");
        if (input.kind !== undefined && !["teaching", "nudge"].includes(input.kind)) throw new policy.PolicyError("Choose a supported notification type.");
        // Bounded, sampled estimate. Never call this an exact count of recipients.
        const registrations = await db().collection("messageInstallations").limit(1000).get();
        const testIds = input.audience === "test" ? await testDeviceIds(registrations.docs) : new Set();
        return { eligible: registrations.docs.filter(d => policy.eligible(d.data(), input, now, testIds, d.id)).length,
          sampled: registrations.size === 1000, scanned: registrations.size };
      }
      case "createCampaign": {
        authorize(request, true);
        const campaignRef = ref("campaigns", input.campaignId);
        const contentId = policy.id(input.contentId);
        if (!["test", "all"].includes(input.audience)) throw new policy.PolicyError("Choose a test or general audience.");
        const timezone = policy.timezone(input.timezone);
        const content = await revision(contentId, input.revision);
        // Content is held by revision. An edit never creates another send.
        const contract = { contentId, revision: input.revision, audience: input.audience,
          sendAt: input.sendAt, expiresAt: input.expiresAt, timezone };
        return await db().runTransaction(async tx => {
          const prior = await tx.get(campaignRef);
          const publication = await tx.get(ref("publishedContent", contentId));
          const job = await tx.get(ref("messagePublicationJobs", contentId));
          if (prior.exists) {
            if (prior.data().request !== JSON.stringify(contract)) {
              throw new HttpsError("already-exists", "This send ID belongs to a different request.");
            }
            return { campaignId: campaignRef.id, state: prior.data().state };
          }
          policy.window(input.sendAt, input.expiresAt, now);
          const destination = job.data()?.projection?.revision === input.revision
            ? job.data().projection : publication.data();
          if (!destination || destination.revision !== input.revision || destination.publishedAt > input.sendAt ||
              destination.expiresAt < input.expiresAt) throw new policy.PolicyError("Publish or schedule this revision before sending it.");
          tx.create(campaignRef, { ...contract, request: JSON.stringify(contract),
            title: content.title, summary: content.summary, state: "queued", createdBy: uid, createdAt: now,
            cursor: null, leaseUntil: 0, accepted: 0, failed: 0, skipped: 0 });
          audit(tx, uid, "createCampaign", campaignRef.id);
          return { campaignId: campaignRef.id, state: "queued" };
        });
      }
      case "createNotificationCampaign": {
        authorize(request, true);
        const campaignRef = ref("campaigns", input.campaignId);
        const notification = policy.nudge(input);
        if (input.contentId !== undefined || input.revision !== undefined) throw new policy.PolicyError("A notification-only send does not need a message.");
        if (!["test", "all"].includes(input.audience)) throw new policy.PolicyError("Choose a test or general audience.");
        const timezone = policy.timezone(input.timezone);
        const contract = { ...notification, audience: input.audience,
          sendAt: input.sendAt, expiresAt: input.expiresAt, timezone };
        return await db().runTransaction(async tx => {
          const prior = await tx.get(campaignRef);
          if (prior.exists) {
            if (prior.data().request !== JSON.stringify(contract)) {
              throw new HttpsError("already-exists", "This send ID belongs to a different request.");
            }
            return { campaignId: campaignRef.id, state: prior.data().state };
          }
          policy.window(input.sendAt, input.expiresAt, now);
          tx.create(campaignRef, { ...contract, request: JSON.stringify(contract), state: "queued",
            createdBy: uid, createdAt: now, cursor: null, leaseUntil: 0, accepted: 0, failed: 0, skipped: 0 });
          audit(tx, uid, "createNotificationCampaign", campaignRef.id);
          return { campaignId: campaignRef.id, state: "queued" };
        });
      }
      case "cancelCampaign": {
        authorize(request, true);
        await db().runTransaction(async tx => {
          const campaign = ref("campaigns", input.campaignId);
          const prior = await tx.get(campaign);
          if (!prior.exists) throw new HttpsError("not-found", "That send was not found.");
          if (["queued", "sending"].includes(prior.data().state)) {
            tx.update(campaign, { state: "cancelled", cancelledAt: now });
            audit(tx, uid, "cancelCampaign", input.campaignId);
          }
        });
        return { ok: true };
      }
      case "pause": {
        authorize(request, true);
        if (typeof input.paused !== "boolean") throw new policy.PolicyError("Choose whether to pause sending.");
        await db().runTransaction(async tx => {
          tx.set(db().doc("messageControls/sending"), { paused: input.paused, uid, changedAt: now });
          audit(tx, uid, input.paused ? "pause" : "resume", "sending");
        });
        return { paused: input.paused };
      }
      case "testDevice": {
        authorize(request, true);
        const device = ref("messageInstallations", input.installationId);
        if (!(await device.get()).exists) throw new HttpsError("not-found", "Open the app on this phone first.");
        const target = ref("messageTestDevices", input.installationId);
        if (input.enabled === true) await target.set({ label: policy.text(input.label, 100), addedBy: uid, addedAt: now });
        else if (input.enabled === false) await target.delete();
        else throw new policy.PolicyError("Choose whether this is a test phone.");
        return { ok: true };
      }
      case "listDevices": {
        authorize(request, true);
        let query = db().collection("messageInstallations").orderBy(FieldPath.documentId()).limit(50);
        if (input.after) query = query.startAfter(policy.id(input.after));
        const snapshot = await query.get();
        const testIds = await testDeviceIds(snapshot.docs);
        return { items: snapshot.docs.map(doc => {
          const { token, ...visible } = doc.data();
          return { id: doc.id, ...visible, test: testIds.has(doc.id) };
        }), next: snapshot.size === 50 ? snapshot.docs.at(-1).id : null };
      }
      case "listCampaigns": {
        authorize(request, true);
        let query = db().collection("campaigns").orderBy(FieldPath.documentId()).limit(50);
        if (input.after) query = query.startAfter(policy.id(input.after));
        const snapshot = await query.get();
        return { items: snapshot.docs.map(d => ({ id: d.id, ...d.data() })),
          next: snapshot.size === 50 ? snapshot.docs.at(-1).id : null,
          paused: (await db().doc("messageControls/sending").get()).data()?.paused === true };
      }
      case "campaignDetails": {
        authorize(request, true);
        const campaign = ref("campaigns", input.campaignId);
        let query = campaign.collection("dispatch").orderBy(FieldPath.documentId()).limit(50);
        if (input.after) query = query.startAfter(policy.id(input.after));
        const snapshot = await query.get();
        return { campaign: (await campaign.get()).data() || null,
          attempts: snapshot.docs.map(d => ({ id: d.id, ...d.data() })),
          next: snapshot.size === 50 ? snapshot.docs.at(-1).id : null };
      }
      case "listHistory": {
        authorize(request, true);
        let query = db().collection("messageAudit").orderBy("at", "desc")
          .orderBy(FieldPath.documentId(), "desc").limit(50);
        if (input.after) {
          const cursor = await ref("messageAudit", input.after).get();
          if (!cursor.exists) throw new HttpsError("not-found", "Reload the history to continue.");
          query = query.startAfter(cursor);
        }
        const snapshot = await query.get();
        return { items: snapshot.docs.map(d => ({ id: d.id, ...d.data() })),
          next: snapshot.size === 50 ? snapshot.docs.at(-1).id : null };
      }
      default: throw new HttpsError("invalid-argument", "Choose a supported action.");
    }
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    // Do not return database paths, provider details, or tokens to a browser.
    if (error instanceof policy.PolicyError) throw new HttpsError("invalid-argument", error.message);
    // Log diagnostics without request data, provider messages, or device tokens.
    console.error("adminMessages failed", { type: error?.name || "UnknownError" });
    throw new HttpsError("internal", "We could not finish that action. Please try again.");
  }
});

async function testDeviceIds(devices) {
  const snapshots = await Promise.all(devices.map(device =>
    db().collection("messageTestDevices").doc(device.id).get()));
  return new Set(snapshots.filter(doc => doc.exists).map(doc => doc.id));
}

async function dispatchCampaign(campaignRef) {
  const lease = require("node:crypto").randomUUID();
  const campaign = await db().runTransaction(async tx => {
    const snapshot = await tx.get(campaignRef);
    const data = snapshot.data();
    const now = Date.now();
    if (!data || !["queued", "sending"].includes(data.state) || data.sendAt > now || data.leaseUntil > now) return null;
    if (data.expiresAt <= now) { tx.update(campaignRef, { state: "expired" }); return null; }
    tx.update(campaignRef, { state: "sending", lease, leaseUntil: now + 540000 });
    return data;
  });
  if (!campaign) return;
  try {
    let query = db().collection("messageInstallations").orderBy(FieldPath.documentId()).limit(50);
    if (campaign.cursor) query = query.startAfter(campaign.cursor);
    const registrations = await query.get();
    const testIds = campaign.audience === "test" ? await testDeviceIds(registrations.docs) : new Set();
    for (const registration of registrations.docs) {
      const dispatchRef = campaignRef.collection("dispatch").doc(registration.id);
      // Recheck each recipient after enumeration, including cancellation and opt-out.
      const claim = await db().runTransaction(async tx => {
        const [current, control, device, previous, content] = await Promise.all([
          tx.get(campaignRef), tx.get(db().doc("messageControls/sending")), tx.get(registration.ref),
          tx.get(dispatchRef), campaign.kind === "nudge" ? Promise.resolve(null)
            : tx.get(ref("publishedContent", campaign.contentId))]);
        const c = current.data();
        const now = Date.now();
        if (c.state !== "sending" || c.lease !== lease || control.data()?.paused || c.expiresAt <= now) return { stop: true };
        const published = content?.data();
        if (c.kind !== "nudge" && (!published || published.revision !== c.revision || published.publishedAt > now || published.expiresAt <= now)) {
          tx.update(campaignRef, { state: "cancelled", reason: "content_unavailable" });
          return { stop: true };
        }
        const prior = previous.data();
        if (["accepted", "failed", "skipped"].includes(prior?.state)) return { skip: true };
        if (!device.exists || !policy.eligible(device.data(), c, now, testIds, registration.id)) {
          tx.set(dispatchRef, { state: "skipped", at: now });
          tx.update(campaignRef, { skipped: FieldValue.increment(1) });
          return { skip: true };
        }
        tx.set(dispatchRef, { state: "attempted", attempts: (prior?.attempts || 0) + 1, at: now });
        return { token: device.data().token, attempts: (prior?.attempts || 0) + 1 };
      });
      if (claim.stop) return;
      if (!claim.skip) {
        let result;
        try {
          const providerId = await getMessaging().send({ token: claim.token,
            data: campaign.kind === "nudge"
              ? { kind: "nudge", schemaVersion: "1", campaignId: campaignRef.id, title: campaign.title,
                body: campaign.body, destination: campaign.destination, expiresAt: String(campaign.expiresAt) }
              : { kind: "teaching", schemaVersion: "1", campaignId: campaignRef.id,
                contentId: campaign.contentId, expiresAt: String(campaign.expiresAt) },
            android: { priority: "normal", ttl: Math.min(policy.MAX_LIFETIME, Math.max(0, campaign.expiresAt - Date.now())) } });
          result = { state: "accepted", providerId, at: Date.now(), attempts: claim.attempts };
        } catch (error) {
          const invalid = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(error.code);
          if (invalid) await db().runTransaction(async tx => {
            const latest = await tx.get(registration.ref);
            if (latest.data()?.token === claim.token) tx.delete(registration.ref);
          });
          result = { state: invalid || claim.attempts >= 3 ? "failed" : "retry", code: error.code || "provider-error",
            at: Date.now(), attempts: claim.attempts };
        }
        await db().runTransaction(async tx => {
          tx.set(dispatchRef, result);
          if (result.state === "accepted" || result.state === "failed") {
            tx.update(campaignRef, { [result.state]: FieldValue.increment(1) });
          }
        });
        if (result.state === "retry") return; // Retry this cursor on the next scheduled run.
      }
      await campaignRef.update({ cursor: registration.id });
    }
    if (registrations.size < 50) await db().runTransaction(async tx => {
      const current = await tx.get(campaignRef);
      if (current.data()?.state === "sending" && current.data()?.lease === lease) {
        tx.update(campaignRef, { state: "complete", completedAt: Date.now() });
      }
    });
  } finally {
    await db().runTransaction(async tx => {
      const current = await tx.get(campaignRef);
      if (current.data()?.lease === lease) tx.update(campaignRef, { leaseUntil: 0 });
    });
  }
}

exports.tickMessages = onSchedule({ schedule: "every 30 minutes", region: "us-central1",
  timeoutSeconds: 540, maxInstances: 1 }, async () => {
  const now = Date.now();
  const jobs = await db().collection("messagePublicationJobs").where("dueAt", "<=", now).limit(50).get();
  for (const job of jobs.docs) await db().runTransaction(async tx => {
    const current = await tx.get(job.ref);
    const data = current.data();
    if (!data || data.dueAt > Date.now()) return;
    if (data.projection.expiresAt > Date.now()) tx.set(ref("publishedContent", job.id), data.projection);
    tx.delete(job.ref);
    audit(tx, data.uid, "scheduledPublish", job.id);
  });
  const expired = await db().collection("publishedContent").where("expiresAt", "<=", now).limit(100).get();
  for (const doc of expired.docs) await db().runTransaction(async tx => {
    const current = await tx.get(doc.ref);
    if (current.exists && current.data().expiresAt <= Date.now()) tx.delete(doc.ref);
  });
  if ((await db().doc("messageControls/sending").get()).data()?.paused) return;
  const campaigns = await db().collection("campaigns").where("state", "in", ["queued", "sending"])
    .where("sendAt", "<=", now).orderBy("sendAt").limit(10).get();
  for (const campaign of campaigns.docs) await dispatchCampaign(campaign.ref);
});

// Follow the app's user-document deletion path. Repeated events are harmless.
exports.deleteMessageInstallations = onDocumentDeleted({ document: "users/{uid}",
  region: "us-central1", retry: true }, async event => {
  for (;;) {
    const devices = await db().collection("messageInstallations").where("uid", "==", event.params.uid).limit(200).get();
    if (!devices.size) return;
    const batch = db().batch();
    for (const device of devices.docs) {
      batch.delete(device.ref);
      batch.delete(db().collection("messageTestDevices").doc(device.id));
    }
    await batch.commit();
  }
});
