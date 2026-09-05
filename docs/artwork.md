# Artwork and font provenance

All three bitmap assets were generated with the built-in image-generation tool for this redesign. They are bundled locally and require no runtime image service. Originals were copied into the project without altering the image content or alpha channel.

| File in `frontend/android/src/main/res/drawable-nodpi` | Use |
| --- | --- |
| `lotus_morning.png` | Welcome, home, family, Sandhya, and secondary introductions |
| `ritual_still_life.png` | Parent sign-in, progress, sankalpa, and Purashcharana |
| `lotus_emblem.png` | Raster brand emblem, launch icon, empty states, and japa |

The launcher wraps the transparent emblem in an Android inset drawable to keep it inside the adaptive-icon safe area. Standard native Material control icons and functional charts remain native UI; decorative vector logos and the drawn lamp have been replaced.

## Generation prompts

### Lotus morning

Use case: photorealistic-natural. Asset type: bundled photographic hero for Anushtanam, a quiet Indian mantra and meditation Android app. Create one premium editorial botanical photograph, landscape 3:2. A real white lotus with the faintest dusty mauve pink at the tips of translucent petals, opening above calm pale sage water, large softly blurred lotus leaves behind. Lotus placed on right half, left half airy almost-white mist and reflected daylight. Close photography with believable delicate petal veins, fine water droplets, gently imperfect natural symmetry, restrained colors. Soft early morning light, subtle film grain, an elegant contemplative botanical journal feel. Dominantly warm white, muted sage, just a little lotus mauve. No bright pink, no red, no purple gradient, no gold, no illustration, no vector, no lettering, no frame, no religious symbols, no watermark. Intended to be cropped as a wide card and as a portrait onboarding panel.

### Ritual still life

Use case: photorealistic-natural. Asset type: decorative landscape photograph for Anushtanam native meditation and mantra app. Create a quiet fine art editorial still life, horizontal 3:2: a simple loop of natural pale sandalwood japa mala prayer beads with one small muted mauve cotton tassel resting on ivory handmade cotton cloth on a pale stone windowsill; a small brass diya with a tiny steady flame farther behind, out of focus. Soft early morning window light from left, gentle shadows. Tactile linen, matte pale beads, no lavish ornament. The beads predominantly in lower right half, airy warm white negative space left and upper third. Very restrained neutral palette, pale sand, white, faint sage cast in background. Human quiet, grounded, beautiful imperfect material texture, real photography with subtle grain. No text, no logos, no watermark, no bright pink or red, no vector illustration, no gradients or UI, no symbols or people.

### Lotus emblem

Use case: product-mockup. Asset type: raster app brand emblem and Android launcher foreground for a contemplative Indian mantra app. One beautiful small lotus flower sculpture in unglazed ivory porcelain, three-quarter view facing camera, opening symmetrical petals with whisper of dusty mauve at their tips, very pale natural clay center. Photorealistic studio macro object with delicately uneven ceramic grain, soft neutral lighting, exceptional sculptural simplicity. Isolated centered with ample transparent space around all sides, the lotus must fit inside central 60 percent of square canvas for adaptive icon safe zone. Truly transparent background with alpha, no floor or colored disc, no lettering, no watermark, no leaves or stems, no decorative lines, no vector illustration, no mandala, no bright pink or red or gold. The flower must read clearly as a simple physical lotus at small size.

## Fonts

Newsreader and DM Sans are bundled as variable fonts under the SIL Open Font License. Source: [Google Fonts / Newsreader](https://github.com/google/fonts/tree/main/ofl/newsreader), [Google Fonts / DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans). License copies live in `docs/licenses`. Android's system fallback supplies Devanagari glyphs.
