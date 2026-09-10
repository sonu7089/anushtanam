"use strict";

const ID = /^[A-Za-z0-9_-]{1,100}$/;
const MAX_LIFETIME = 28 * 24 * 60 * 60 * 1000;
function id(value) {
  if (typeof value !== "string" || !ID.test(value)) throw new Error("Choose a valid ID.");
  return value;
}
function text(value, max, required = true) {
  if (typeof value !== "string" || value.length > max || (required && !value.trim())) {
    throw new Error(`Enter text between ${required ? 1 : 0} and ${max} characters.`);
  }
  return value.trim();
}
function content(input) {
  if (!input || input.locale !== "en" || !["teaching", "message", "announcement"].includes(input.type)) {
    throw new Error("Choose an English teaching, message, or announcement.");
  }
  const art = input.art || "river";
  if (!["river", "lotus", "jasmine", "diya"].includes(art)) throw new Error("Choose one of the supported pictures.");
  return {
    schemaVersion: 1, locale: "en", type: input.type,
    title: text(input.title, 100), summary: text(input.summary, 280), body: text(input.body, 20000),
    attribution: text(input.attribution || "", 160, false), art, pinned: input.pinned === true
  };
}
function window(start, end, now) {
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= end || end <= now || end > 8640000000000000) {
    throw new Error("Choose an end date after the start date and after today.");
  }
}
function eligible(registration, campaign, now, testIds, registrationId) {
  return registration.enabled === true && registration.schemaVersion === 1 && registration.locale === "en" &&
    typeof registration.token === "string" && registration.token.length > 0 &&
    registration.updatedAt?.toMillis() > now - 30 * 86400000 &&
    (campaign.audience === "all" || testIds.has(registrationId));
}
module.exports = { id, text, content, window, eligible, MAX_LIFETIME };
