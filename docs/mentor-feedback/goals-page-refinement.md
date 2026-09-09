# Goals page refinement

The page opened by Home > Your goal now leads with the recorded count, target count, remaining japa and a horizontal progress bar. It uses a small mala illustration and the existing app typography, colors, cards and buttons. Continue japa opens the Japa tab; the toolbar returns to the previous page.

Completed goals offer a new goal. Past goals use compact rows. The current completed goal is not repeated in history. Parents with children can still set a child goal, with a child selected when the form opens. Setting a goal aside retains the confirmation and existing accounting behavior.

The goal page names the practice as Gayatri Mantra japa. The How to practise component is removed from the Japa tab and its voice-model loading fallback. Guide access elsewhere is unchanged.

Validation: debug build and the original four GoalsUiTest emulator tests passed before the guide component was removed; the obsolete guide-link test was removed with it. Visual captures cover active, empty and completed goals, dark mode and 1.5x text. Existing goal accounting changes are preserved.

Captures are in docs/ui-review with goals-redesign and goals-large-text suffixes. The root app-debug.apk contains this update.
