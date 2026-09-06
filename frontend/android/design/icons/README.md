# Navigation icons

The navigation uses Google Material Symbols Rounded, 24px, weight 400, unfilled. These are the original standard SVG paths, compiled locally into Android VectorDrawables. Runtime colors come from the app theme.

| File | Google symbol |
| --- | --- |
| `nav_home.svg` | `home` |
| `nav_practice.svg` | `menu_book` |
| `nav_japa.svg` | `self_improvement` |
| `nav_progress.svg` | `bar_chart` |
| `nav_children.svg` | `group` |
| `nav_settings.svg` | `settings` |

Source: [Google Material Symbols](https://github.com/google/material-design-icons/tree/master/symbols/web). Download pattern: `symbols/web/<name>/materialsymbolsrounded/<name>_24px.svg`. Retrieved 6 September 2026. [Official guide](https://developers.google.com/fonts/docs/material_symbols). Apache 2.0 license is in `docs/licenses/material-symbols-APACHE-2.0.txt`.

`arrow_forward.svg` is the app's own trailing action arrow from the earlier UI pass.

Run `python scripts/generate-navigation-icons.py` after editing. The converter preserves the SVG view box, including the negative Y origin used by Material Symbols. Keep sources path-only or extend the converter when adding other SVG elements.

Tab highlighting now fades in place over 140ms. It does not slide or spring across intervening tabs. Route state, parent/child destinations, accessible names, and touch targets are unchanged.
