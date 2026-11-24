const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const data = JSON.parse(fs.readFileSync(path.join(__dirname, 'listing.json'), 'utf8'));
const out = path.join(root, 'listing');
fs.mkdirSync(out, { recursive: true });
const fields = [
  ['title', 'app-title.txt', 30],
  ['shortDescription', 'short-description.txt', 80],
  ['fullDescription', 'full-description.txt', 4000],
];
const validation = fields.map(([key, filename, limit]) => {
  const value = data[key];
  const count = [...value].length;
  if (count > limit || value.includes('\u2014')) throw new Error(`Invalid copy: ${key}`);
  fs.writeFileSync(path.join(out, filename), value, 'utf8');
  return { field: key, characters: count, limit };
});
fs.writeFileSync(path.join(out, 'validation.json'), JSON.stringify(validation, null, 2) + '\n');
fs.writeFileSync(path.join(out, 'policy-links.txt'), `Privacy policy: ${data.privacyPolicy}\nAccount deletion: ${data.accountDeletion}\n`);
const alt = [
  ['app-icon-512.png', 'A pale lotus on a soft rose background, matching the Anushtanam app icon.'],
  ['feature-graphic-1024x500.png', 'A lotus in morning light beside the words A little time. A daily practice.'],
  ['01-daily-practice.png', 'Daily japa, practice options and progress toward a personal goal on the Home screen.'],
  ['02-mental-japa.png', 'Mental japa counter with a saved count, a resume button and an option to save the session.'],
  ['03-personal-goals.png', 'A Gayatri Mantra goal showing 36 of 108 japa completed, with a past goal below.'],
  ['04-sandhya-reminders.png', 'Dark Sandhya settings with a chosen region, morning, midday and evening counts, and daily reminders.'],
  ['05-teachings-and-notes.png', 'The Messages screen with a gentle note about giving daily practice time and attention.'],
  ['06-dark-theme.png', 'Home in dark mode, with the lotus artwork, daily japa count and personal goal.'],
];
for (const [,text] of alt) if (text.length > 140) throw Error('Alt text too long');
fs.writeFileSync(path.join(out, 'image-descriptions.txt'), alt.map(([file,text])=>`${file}\n${text}\n`).join('\n'));
const doc = `# Play Store listing copy\n\n## App title\n\n${data.title}\n\n## Short description\n\n${data.shortDescription}\n\n## Full description\n\n${data.fullDescription}\n\n## Image descriptions\n\n${alt.map(([file,text])=>`- **${file}:** ${text}`).join('\n')}\n\n## Policy links\n\n- [Privacy policy](${data.privacyPolicy})\n- [Account deletion](${data.accountDeletion})\n\n## Character checks\n\n${validation.map(v=>`- ${v.field}: ${v.characters} / ${v.limit}`).join('\n')}\n`;
fs.writeFileSync(path.join(root, 'listing-copy.md'), doc);
console.log(JSON.stringify(validation));
