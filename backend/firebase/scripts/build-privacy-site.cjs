"use strict";

// Hosting and the Android app use the same maintained policy documents.
const fs = require("node:fs");
const path = require("node:path");
const source = path.resolve(__dirname, "../../../playstore-assets");
const output = path.resolve(__dirname, "../privacy-public");
fs.mkdirSync(output, { recursive: true });
for (const [from, to] of [
  ["privacy-policy.html", "index.html"],
  ["privacy-policy.html", "privacy-policy.html"],
  ["delete-account.html", "delete-account.html"]
]) {
  fs.copyFileSync(path.join(source, from), path.join(output, to));
}
console.log("Prepared privacy policy and account-deletion pages for Firebase Hosting.");
