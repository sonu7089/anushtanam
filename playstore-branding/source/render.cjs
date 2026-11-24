/* Rebuild the store pack from the app's own artwork and real UI captures. */
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { createRequire } = require('node:module');
const runtime = process.env.BRANDING_NODE_MODULES;
const dep = runtime ? createRequire(path.join(runtime, '_branding.cjs')) : require;
const { chromium } = dep('playwright');
const sharp = dep('sharp');
const root = path.resolve(__dirname, '..');
const repo = path.resolve(root, '..');
const assets = path.join(__dirname, 'assets');
const upload = path.join(root, 'upload');
const brand = path.join(root, 'brand');
for (const d of [assets, upload, brand, path.join(upload, 'phone-screenshots')]) fs.mkdirSync(d, { recursive: true });

const copy = (from, to) => fs.copyFileSync(path.join(repo, from), path.join(assets, to));
for (const n of ['lotus_emblem', 'lotus_morning']) copy(`frontend/android/design/artwork/masters/${n}.png`, `${n}.png`);
for (const n of ['newsreader', 'dm_sans']) copy(`frontend/android/src/main/res/font/${n}.ttf`, `${n}.ttf`);
fs.copyFileSync(path.join(assets, 'lotus_emblem.png'), path.join(brand, 'lotus-mark-transparent.png'));

const panels = [
  { id: '01-daily-practice', file: 'home-light-goal-sync.png', theme: 'paper', title: 'A little time for yourself.', sub: 'Keep your daily japa close.', alt: 'Daily japa, practice options and progress toward a personal goal on the Home screen.' },
  { id: '02-mental-japa', file: 'sandhya-counter-light-simple-counts.png', theme: 'rose', title: 'One japa at a time.', sub: 'Count your mental japa at your own pace.', alt: 'Mental japa counter with a saved count, a resume button and an option to save the session.' },
  { id: '03-personal-goals', file: 'targets-light-goals-redesign.png', theme: 'sage', title: 'A goal that feels right.', sub: 'Choose your count. Take your time.', alt: 'A Gayatri Mantra goal showing 36 of 108 japa completed, with a past goal below.' },
  { id: '04-sandhya-reminders', file: 'settings-sandhya-dark-mf-six-grouped.png', theme: 'forest', title: 'Make room for Sandhya.', sub: 'Your region, your counts, your reminders.', alt: 'Dark Sandhya settings with a chosen region, morning, midday and evening counts, and daily reminders.' },
  { id: '05-teachings-and-notes', file: 'messages-light-mf-ten-polished.png', theme: 'paper', title: 'A note to keep close.', sub: 'A little encouragement along the way.', alt: 'The Messages screen with a gentle note about giving daily practice time and attention.' },
  { id: '06-dark-theme', file: 'home-dark-goal-sync.png', theme: 'forest', title: 'Settle into the evening.', sub: 'A familiar space, in light or dark.', alt: 'Home in dark mode, with the lotus artwork, daily japa count and personal goal.' },
];
for (const p of panels) copy(`docs/ui-review/${p.file}`, p.file);

const css = `
@font-face{font-family:Newsreader;src:url('assets/newsreader.ttf')} 
@font-face{font-family:DM;src:url('assets/dm_sans.ttf')}
*{box-sizing:border-box}body{margin:0;font-family:DM,sans-serif;color:#263d30;background:#f8f6f1}
.canvas{position:relative;overflow:hidden;width:1080px;height:1920px;background:#f6f3ed}
.rose{background:#f2e5ed;color:#382331}.sage{background:#e4ede6}.forest{background:#263d30;color:#f2e5ed}
.identity{position:absolute;top:44px;left:72px;font-family:Newsreader,serif;font-size:34px;letter-spacing:-.4px}
.sequence{position:absolute;right:74px;top:52px;font-size:18px;letter-spacing:3px;opacity:.65}
h1{margin:0;position:absolute;top:109px;left:65px;right:65px;font:400 76px/1.08 Newsreader,serif;letter-spacing:-2px;text-align:center}
.sub{position:absolute;top:204px;left:65px;right:65px;font-size:30px;line-height:1.3;text-align:center;color:#50695b}
.forest .sub{color:#c9d9cb}.rose .sub{color:#795469}
.screen{position:absolute;left:154px;top:289px;width:772px;height:1589px;border:2px solid #ffffff9c;border-radius:25px;overflow:hidden;box-shadow:0 20px 70px #20362920;background:#fff}
.screen img{position:absolute;width:768px;height:auto;left:0;top:-80px;display:block}
.forest .screen{border-color:#63776b;background:#151816;box-shadow:0 20px 70px #0b171940}
.feature{width:1024px;height:500px;background:#f2ebdf}
.feature .photo{position:absolute;right:0;top:0;width:770px;height:500px;object-fit:cover;object-position:right center}
.feature .wash{position:absolute;inset:0;background:linear-gradient(90deg,#f2ebdf 0%,#f2ebdf 28%,#f2ebdfee 41%,#f2ebdf22 70%,transparent 100%)}
.feature .identity{top:63px;left:67px;font-size:30px}
.feature h1{top:153px;left:65px;right:auto;width:560px;text-align:left;font-size:67px;line-height:1.04;letter-spacing:-1.7px}
.feature .sub{top:325px;left:67px;right:auto;text-align:left;font-size:23px;color:#50695b}
.feature .rule{position:absolute;left:68px;top:385px;width:64px;height:3px;background:#795469}
.icon{width:512px;height:512px;background:#f2e5ed;display:grid;place-items:center}.icon img{width:64%;height:64%;object-fit:contain}
.lockup{width:1600px;height:560px;background:#f7f4ee;display:flex;align-items:center;padding:60px 100px;gap:34px}.lockup img{width:360px;height:360px;object-fit:contain}.lockup .name{font:400 118px/1.1 Newsreader,serif;letter-spacing:-3px}.lockup .tag{font-size:31px;color:#50695b;margin-top:24px}.lockup.forest{background:#263d30}.lockup.forest .tag{color:#cad9ce}
`;
const html = body => `<!doctype html><html lang="en"><meta charset="utf-8"><title>Anushtanam branding</title><style>${css}</style><body>${body}</body></html>`;
const layouts = [];
function layout(file, markup, output, width, height, alpha=false) {
  fs.writeFileSync(path.join(__dirname, file), html(markup));
  layouts.push({file,output,width,height,alpha});
}
for (const [i,p] of panels.entries()) {
  layout(`${p.id}.html`, `<main class="canvas ${p.theme}"><div class="identity">Anushtanam</div><div class="sequence">0${i+1} / 06</div><h1>${p.title}</h1><div class="sub">${p.sub}</div><div class="screen"><img src="assets/${p.file}" alt="${p.alt}"></div></main>`, `upload/phone-screenshots/${p.id}.png`,1080,1920);
}
layout('feature.html', '<main class="canvas feature"><img class="photo" src="assets/lotus_morning.png"><div class="wash"></div><div class="identity">Anushtanam</div><h1>A little time.<br>A daily practice.</h1><div class="sub">Gayatri japa, at your own pace.</div><div class="rule"></div></main>', 'upload/feature-graphic-1024x500.png',1024,500);
layout('icon.html','<main class="icon"><img src="assets/lotus_emblem.png" alt="Lotus"></main>','upload/app-icon-512.png',512,512,true);
layout('logo-light.html','<main class="lockup"><img src="assets/lotus_emblem.png"><div><div class="name">Anushtanam</div><div class="tag">A little time for yourself.</div></div></main>','brand/logo-lockup-light.png',1600,560);
layout('logo-dark.html','<main class="lockup forest"><img src="assets/lotus_emblem.png"><div><div class="name">Anushtanam</div><div class="tag">A little time for yourself.</div></div></main>','brand/logo-lockup-dark.png',1600,560);

async function main() {
  const browser = await chromium.launch({headless:true,channel:process.env.BRANDING_BROWSER || 'chrome'});
  const report=[];
  try {
    const page=await browser.newPage({deviceScaleFactor:1});
    for(const l of layouts){
      await page.setViewportSize({width:l.width,height:l.height});
      await page.goto(pathToFileURL(path.join(__dirname,l.file)).href);
      await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));});
      const screenshot=await page.screenshot({type:'png',animations:'disabled'});
      let exportImage=sharp(screenshot);
      exportImage=l.alpha?exportImage.ensureAlpha():exportImage.removeAlpha();
      const destination=path.join(root,l.output);
      await exportImage.png({compressionLevel:9}).toFile(destination);
      const meta=await sharp(destination).metadata();
      if(meta.width!==l.width||meta.height!==l.height||meta.hasAlpha!==l.alpha) throw Error(`Invalid export ${l.output}`);
      const bytes=fs.statSync(destination).size;
      if(l.alpha&&bytes>1024*1024) throw Error('Icon exceeds 1024 KB');
      if(!l.alpha&&bytes>8*1024*1024) throw Error(`Asset exceeds 8 MB: ${l.output}`);
      report.push({file:l.output,width:meta.width,height:meta.height,channels:meta.channels,bytes});
      console.log(l.output,meta.width,meta.height,bytes);
    }
    // Larger logo export is rendered from the master, never upscaled from the 512px icon.
    await page.setViewportSize({width:1024,height:1024});
    await page.goto(pathToFileURL(path.join(__dirname,'icon.html')).href);
    await page.addStyleTag({content:'.icon{width:1024px;height:1024px}'});
    await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));});
    await page.screenshot({path:path.join(brand,'app-icon-1024.png')});

    const preview=`<!doctype html><html lang="en"><meta charset="utf-8"><title>Anushtanam | Play Store pack</title><style>@font-face{font-family:Newsreader;src:url('source/assets/newsreader.ttf')}@font-face{font-family:DM;src:url('source/assets/dm_sans.ttf')}*{box-sizing:border-box}body{margin:0;background:#f6f3ed;color:#263d30;font-family:DM,sans-serif;padding:55px 60px}h1{font:56px Newsreader;margin:0 0 12px}p{font-size:20px;line-height:1.5;margin:0 0 28px;color:#50695b}.hero{display:grid;grid-template-columns:230px 1fr;gap:32px;align-items:center;margin-bottom:38px}.icon{width:230px;border-radius:45px}.feature{width:100%;border-radius:12px}.screens{display:grid;grid-template-columns:repeat(3,1fr);gap:25px}.screens img{width:100%;border-radius:12px}.screens a{color:inherit;text-decoration:none}figcaption{font-size:16px;padding:10px 0 7px}figure{margin:0}.note{margin-top:30px;font-size:16px}a{color:#795469}</style><h1>A little time for yourself.</h1><p>Anushtanam · Play Store branding pack</p><div class="hero"><a href="upload/app-icon-512.png"><img class="icon" src="upload/app-icon-512.png" alt="App icon"></a><a href="upload/feature-graphic-1024x500.png"><img class="feature" src="upload/feature-graphic-1024x500.png" alt="Feature graphic"></a></div><div class="screens">${panels.map(p=>`<figure><a href="upload/phone-screenshots/${p.id}.png"><img src="upload/phone-screenshots/${p.id}.png" alt="${p.alt}"></a><figcaption>${p.title}</figcaption></figure>`).join('')}</div><p class="note">Six panels using saved app captures with demonstration data. See README.md for source details.<br>Listing copy and image descriptions are in <a href="listing-copy.md">listing-copy.md</a>. No video assets.</p></html>`;
    fs.writeFileSync(path.join(root,'preview.html'),preview);
    await page.setViewportSize({width:1500,height:1000});
    await page.goto(pathToFileURL(path.join(root,'preview.html')).href);
    await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));});
    await page.screenshot({path:path.join(root,'preview.png'),fullPage:true});
  } finally {await browser.close();}
  fs.writeFileSync(path.join(root,'validation.json'),JSON.stringify({created:'2026-09-24',assets:report},null,2)+'\n');
  fs.writeFileSync(path.join(root,'source-provenance.json'),JSON.stringify({screenshots:panels.map(p=>({export:`upload/phone-screenshots/${p.id}.png`,source:`docs/ui-review/${p.file}`,kind:'existing Android design-review capture with fixture data',treatment:'Unaltered UI image placed in an HTML layout. Status bar and bottom system area clipped by the frame. No UI text or values changed.'})),artwork:['frontend/android/design/artwork/masters/lotus_emblem.png','frontend/android/design/artwork/masters/lotus_morning.png'],fonts:['Newsreader','DM Sans']},null,2)+'\n');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
