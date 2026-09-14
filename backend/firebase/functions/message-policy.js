"use strict";

class PolicyError extends Error {}

const ID = /^[A-Za-z0-9_-]{1,100}$/;
const MAX_LIFETIME = 28 * 24 * 60 * 60 * 1000;
function id(value) {
  if (typeof value !== "string" || !ID.test(value)) throw new PolicyError("Choose a valid ID.");
  return value;
}
function text(value, max, required = true) {
  if (typeof value !== "string" || value.length > max || (required && !value.trim())) {
    throw new PolicyError(`Enter text between ${required ? 1 : 0} and ${max} characters.`);
  }
  return value.trim();
}
function content(input) {
  if (!input || input.locale !== "en" || !["teaching", "message", "announcement"].includes(input.type)) {
    throw new PolicyError("Choose an English teaching, message, or announcement.");
  }
  const art = input.art || "river";
  if (!["river", "lotus", "jasmine", "diya"].includes(art)) throw new PolicyError("Choose one of the supported pictures.");
  return {
    schemaVersion: 1, locale: "en", type: input.type,
    title: text(input.title, 100), summary: text(input.summary, 280), body: text(input.body, 20000),
    attribution: text(input.attribution || "", 160, false), art, pinned: input.pinned === true
  };
}
function window(start, end, now) {
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= end || end <= now || end > 8640000000000000) {
    throw new PolicyError("Choose an end date after the start date and after today.");
  }
}
function eligible(registration, campaign, now, testIds, registrationId) {
  return registration.enabled === true && registration.schemaVersion === 1 && registration.locale === "en" &&
    (campaign.kind !== "nudge" || registration.notificationOnly === true) &&
    typeof registration.token === "string" && registration.token.length > 0 &&
    registration.updatedAt?.toMillis() > now - 30 * 86400000 &&
    (campaign.audience === "all" || testIds.has(registrationId));
}
function nudge(input) {
  if (!input || input.destination !== "home") throw new PolicyError("Choose Home as the notification destination.");
  return { kind: "nudge", title: text(input.title, 100), body: text(input.body, 280), destination: "home" };
}
function timezone(value) {
  const zone = text(value, 80);
  try { new Intl.DateTimeFormat("en", { timeZone: zone }); }
  catch { throw new PolicyError("Choose a valid timezone."); }
  return zone;
}
module.exports = { PolicyError, timezone, id, text, content, window, eligible, nudge, MAX_LIFETIME };
