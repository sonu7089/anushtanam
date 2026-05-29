# Mantra Siddhi (मंत्र सिद्धि) - Demo Walkthrough Script

This script guides you through demonstrating the core capabilities, user stories, and architecture of the **Mantra Siddhi** app during a demo.

---

## Preparation
1. Ensure the application is installed and run on an Android device or emulator (Android 8.0 / API 26+).
2. On first launch, the app automatically pre-seeds the Room database with rich narrative demo data representing distinct user stories.
3. The app functions **100% offline**, leveraging the on-device Android SpeechRecognizer API. No network connection is required.

---

## Walkthrough Scenario 1: Individual Mode (Arjun)
*Arjun is a consistent individual practitioner with 15 historical sessions, an 85% average recitation score, and a progress target.*

1. **Launch the App**:
   - The app launches with a warm saffron-gold animated splash screen displaying the custom lotus symbol.
   - You are navigated to the profile selection screen.
2. **Select Profile**:
   - Select **Arjun** (Individual profile).
3. **Explore Dashboard**:
   - Point out the clean Material 3 UI tailored in spiritual saffron (`#FFFF6F00`).
   - Observe Arjun's savings card showing a **Total Saved** of `₹10.00` and current progress on his target: `45/108 recitations`.
4. **View Progress Analytics**:
   - Tap on the **Progress** navigation tab.
   - Point out the visual analytics drawn using Custom Jetpack Compose Canvas:
     - **Overview**: Highlights a streak of 6 days, average accuracy of 85%, and total recitations.
     - **Daily Recitations (Bar Chart)**: Rich gradient-filled bar chart showing recitations over the last week.
     - **Accuracy Trend (Line Chart)**: Smooth bezier curve displaying performance.
     - **Word Mastery (Heat Map)**: Grid showing per-word pronunciation quality. Notice that "svah" or "prachodayat" are colored in warm hues if they need improvement.
5. **Practice Recitation**:
   - Go to the **Practice** tab.
   - Play the benchmark audio. Show how it highlights each of the 14 words of the Gayatri Mantra in real-time.
   - Tap the microphone button, grant audio permission if requested, and recite the mantra.
   - Point out the per-word phonetic feedback, color-coded based on accuracy score, with suggestions for hard words.
6. **Recite Mode**:
   - Navigate to **Recitation** tab and tap **Begin**.
   - Speak into the mic. Note the haptic buzz on button taps and correct recitation, and the automatic count increment once threshold (0.85) is met.

---

## Walkthrough Scenario 2: Parent & Child Mode (Meera & Aarav)
*Meera is the parent who sets incentives for her child Aarav to practice.*

1. **Switch Profile**:
   - Navigate to the **Settings** tab and tap **Switch Profile**.
   - Select **Meera** (Parent profile).
2. **View Child Progress**:
   - Observe the **Child Progress** tab, exclusive to Parent profiles.
   - View Aarav's statistics: average score of 65% across 12 recitations.
3. **Set Child Goal / Incentive**:
   - Tap on **Targets** tab.
   - Tap **Create Target**, select **Aarav**, set target to `108 recitations`, and input an incentive amount of `₹5.00`.
   - Tap Save. The new target is successfully saved in the local Room database.
4. **Log in as Aarav (Child)**:
   - Go to settings, tap **Switch Profile**, and select **Aarav** (Child profile).
   - Point out the tailored child view:
     - **No ability to set targets** or view other profiles.
     - Visible savings displaying his **Locked Savings** (₹0.56) which will unlock once the target set by Meera is completed.
5. **Simulate Completion**:
   - Recite the mantra in Recitation Mode to show progress increments.
   - Once the target reaches `108/108`, a full-screen **Celebration Overlay** appears, playing the achievement chimes, vibrating the device, and unlocking the `₹5.00` savings.

---

## Walkthrough Scenario 3: Settings & Edge Cases
1. **Theme Switching**:
   - Navigate to **Settings** and toggle between Light and Dark mode. Show the premium glassmorphism and deep indigo dark mode colors.
2. **Recitation Sensitivity**:
   - Show the **Sensitivity Slider** in Settings. This dynamically shifts the fuzzy matching correctness threshold (0.75 to 0.95), adapting the app for absolute beginners or advanced practitioners.
3. **Reset Database**:
   - Tap **Reset Demo Data** in Settings. Point out that the local SQLite database is instantly re-seeded to its clean starting state, allowing immediate re-runs of the demo.
