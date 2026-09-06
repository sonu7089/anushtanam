# Navigation icons

Original SVG sources drawn for Anushtanam. All use a 24 by 24 view box, 1.75-unit strokes, round joins, and round caps. They can be opened in any SVG editor.

| Source | Destination |
| --- | --- |
| `nav_home.svg` | Home, with a welcoming arched doorway |
| `nav_practice.svg` | Practice, an open book |
| `nav_japa.svg` | Japa, a mala and tassel |
| `nav_progress.svg` | Progress, a rising chart |
| `nav_children.svg` | Children, two figures |
| `nav_settings.svg` | Settings, three adjustable sliders |
| `arrow_forward.svg` | Trailing action on Home practice cards |

Run `python scripts/generate-navigation-icons.py` from the repository root after editing. The script converts path geometry to Android VectorDrawable XML in the app's drawable directory. No SVG loader or network request is needed at runtime. Keep sources path-only with the shared stroke width, or extend the converter when adding new SVG features.

Compose supplies theme colors, accessible labels, selected-tab semantics, and touch targets. The forward arrow mirrors automatically for right-to-left layouts.
