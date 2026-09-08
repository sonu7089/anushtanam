# Artwork and font provenance

All eight bitmap assets were generated with the built-in image-generation tool for this redesign. They are bundled locally and require no runtime image service. Originals were copied into the project without altering the image content or alpha channel.

| File in `frontend/android/src/main/res/drawable-nodpi` | Use |
| --- | --- |
| `lotus_morning.png` | Welcome, Home, and child sign-in |
| `ritual_still_life.png` | Progress, goals, and Purashcharana |
| `lotus_emblem.png` | Raster brand emblem, launch icon, empty states, and milestones |

The launcher wraps the transparent emblem in an Android inset drawable to keep it inside the adaptive-icon safe area. Navigation uses Google's Material Symbols Rounded, compiled from the upstream SVG sources into Android VectorDrawables. Source details are in [the icon README](../frontend/android/design/icons/README.md); the Apache 2.0 license is in `docs/licenses`. Other Material controls and functional charts remain native UI; decorative logos use bitmap assets.

## Generation prompts

### Lotus morning

Use case: photorealistic-natural. Asset type: bundled photographic hero for Anushtanam, a quiet Indian mantra and meditation Android app. Create one premium editorial botanical photograph, landscape 3:2. A real white lotus with the faintest dusty mauve pink at the tips of translucent petals, opening above calm pale sage water, large softly blurred lotus leaves behind. Lotus placed on right half, left half airy almost-white mist and reflected daylight. Close photography with believable delicate petal veins, fine water droplets, gently imperfect natural symmetry, restrained colors. Soft early morning light, subtle film grain, an elegant contemplative botanical journal feel. Dominantly warm white, muted sage, just a little lotus mauve. No bright pink, no red, no purple gradient, no gold, no illustration, no vector, no lettering, no frame, no religious symbols, no watermark. Intended to be cropped as a wide card and as a portrait onboarding panel.

### Ritual still life

Use case: photorealistic-natural. Asset type: decorative landscape photograph for Anushtanam native meditation and mantra app. Create a quiet fine art editorial still life, horizontal 3:2: a simple loop of natural pale sandalwood japa mala prayer beads with one small muted mauve cotton tassel resting on ivory handmade cotton cloth on a pale stone windowsill; a small brass diya with a tiny steady flame farther behind, out of focus. Soft early morning window light from left, gentle shadows. Tactile linen, matte pale beads, no lavish ornament. The beads predominantly in lower right half, airy warm white negative space left and upper third. Very restrained neutral palette, pale sand, white, faint sage cast in background. Human quiet, grounded, beautiful imperfect material texture, real photography with subtle grain. No text, no logos, no watermark, no bright pink or red, no vector illustration, no gradients or UI, no symbols or people.

### Lotus emblem

Use case: product-mockup. Asset type: raster app brand emblem and Android launcher foreground for a contemplative Indian mantra app. One beautiful small lotus flower sculpture in unglazed ivory porcelain, three-quarter view facing camera, opening symmetrical petals with whisper of dusty mauve at their tips, very pale natural clay center. Photorealistic studio macro object with delicately uneven ceramic grain, soft neutral lighting, exceptional sculptural simplicity. Isolated centered with ample transparent space around all sides, the lotus must fit inside central 60 percent of square canvas for adaptive icon safe zone. Truly transparent background with alpha, no floor or colored disc, no lettering, no watermark, no leaves or stems, no decorative lines, no vector illustration, no mandala, no bright pink or red or gold. The flower must read clearly as a simple physical lotus at small size.

## Additional images, September 2026

Four more images were generated with the built-in image tool. The first three assets are retained. All images are bundled locally. Photographs fade into the current theme background through Compose drawing; no raster image was edited with code.

| Asset | Placement |
| --- | --- |
| `river_dawn.png` | Sandhya introduction |
| `diya_cutout.png` | Japa recording page |
| `jasmine_sprig.png` | Settings edge decoration |
| `family_hands.png` | Parent sign-in, Family, and Children |

The diya and jasmine originals contain alpha transparency. The jasmine includes soft photographic haze; the later checkerboard-background edit was rejected and is not packaged.

### New generation prompts

#### river_dawn.png

Use case: photorealistic-natural. Asset type: immersive backdrop in a calm Indian daily prayer Android app. Create an original fine-art editorial photograph, landscape 1536x1024, of a quiet river at first light in India, low soft reeds in the far right foreground, a subtle distant stone ghat, morning mist, small pale sun reflected on still water. No people or buildings dominating the scene. Upper left and bottom left are spacious luminous ivory haze with little detail so this scene can dissolve into white interface space; the river and botanical details gather on the right and top. Warm natural light, subdued sage, cream, faint honey tones, lifelike water and atmospheric perspective, modest and familiar, no dramatic orange sky. No text, no symbols, no UI, no frames, no watermark.

#### diya_cutout.png

Use case: photorealistic-natural. Asset type: genuinely transparent photographic cutout for a prayer app. A single small handmade unglazed terracotta diya with one short cotton wick and a tiny steady golden flame. Natural reddish-brown clay, slightly irregular handmade texture, a few imperfections, three-quarter view slightly from above. Quiet simple everyday Indian prayer lamp, no metal ornamentation. Whole object including the flame visible with generous transparent space on every side. Realistic soft studio illumination, delicate contact shadow only immediately under the base. Square composition. Isolate on a truly transparent alpha background, not white, no checkerboard or fake background. No plate, flowers, text, extra objects, borders, halos or watermark.

#### jasmine_sprig.png

Use case: photorealistic-natural. Asset type: genuinely transparent botanical photographic cutout for subtle decoration around app content. One slender fresh jasmine sprig with five or six small ivory jasmine blossoms and three muted sage green leaves on a delicate curved stem. Lifelike botanical macro photography, soft daylight, fine natural petal texture. Branch enters from the upper right and arcs gently downward toward the center, spacious composition designed to hang from the right edge of a mobile screen. Entire sprig fits within the image. No bouquet or vase. Portrait 1024x1536. Actual transparent alpha background everywhere around the plant, no solid background, no checkerboard, no border, no text, no watermark.

#### family_hands.png

Use case: photorealistic-natural. Asset type: immersive editorial photograph for a family prayer app. A close, candid scene of an Indian parent and a school-age child sitting side by side on a plain cotton mat near an open sunlit window. Only their relaxed hands and laps are framed, no faces. The parent's gently open hand holds a few simple wooden mala beads while the child's hands rest calmly nearby, natural anatomy and credible fingers, no elaborate pose. Simple ivory cotton sleeves, everyday home, warm sense of time shared quietly. Portrait 1024x1536. Main human detail is on the right half; left half and bottom soften into bright ivory linen and natural defocus, suitable for a white page backdrop. Soft natural light, cream, wood and sage tones. No text, no logos, no sacred writing, no dramatic lighting, no graphic borders.

## Photographic mala

`purashcharana_mala.png` replaces the drawn bead ring on Home and Purashcharana. Its transparent center holds the existing lotus emblem, with the complete tassel visible below. The original is a 1254 x 1254 RGBA PNG. Actual counts and progress remain in the adjoining text and progress controls.

### Generation prompt

Use case: product-mockup. Asset type: transparent photographic mala ring for the Purashcharana screen of a white and charcoal Indian prayer app. Primary request: a real sandalwood japa mala laid flat in a near-circular loop, photographed exactly from above. Only the mala, no other objects. Many small round natural pale sandalwood beads on a fine cream thread, natural subtle wood grain, one slightly larger guru bead at six o'clock and a short soft dusty mauve cotton tassel pointing downward. The loop is centered, circular rather than oval, taking up about 84 percent of a square canvas, with a large completely empty round center for a separate lotus emblem. Whole tassel visible with margin. Gentle neutral studio lighting, believable delicate contact shadows immediately beside beads, tactile real photography, elegant and modest. PNG with a real transparent alpha background everywhere outside the beads and tassel AND inside the ring. Do not render a checkerboard or any backdrop. No logo, lotus, text, markings, frame, gold, mandala ornament, illustration, vector or UI. 1024 by 1024.

## Google sign-in logo

MF-07 uses the [official Google G PNG](https://developers.google.com/static/identity/images/g-logo.png), downloaded on 8 September 2026, in `frontend/android/src/main/res/drawable-nodpi/google_g.png`. It is displayed without alteration in the Google sign-in button. This is a Google brand asset, not generated artwork. Usage guidance: [Google sign-in branding](https://developers.google.com/identity/branding-guidelines).

## Fonts

Newsreader and DM Sans are bundled as variable fonts under the SIL Open Font License. Source: [Google Fonts / Newsreader](https://github.com/google/fonts/tree/main/ofl/newsreader), [Google Fonts / DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans). License copies live in `docs/licenses`. Android's system fallback supplies Devanagari glyphs.
