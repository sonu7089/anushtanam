# Anushtanam

**Anushtanam is an Android app for daily Vedic mantra practice.** It helps a sadhak with japa and sankalpa, listens to recitation and scores pronunciation on the device, tracks progress for the individual and for family members, and sends Sandhya reminders. The Gayatri mantra ships with the app. Other mantras are added as JSON definitions, not code.

## Features

- **Guided recitation:** record a recitation, score it word by word against the reference, and get concrete suggestions for improvement.
- **Japa and sankalpa:** count japa, set targets and goals, and track purashcharana vows.
- **Progress:** per-word mastery, weak words, a practice plan, and history.
- **Sandhya reminders:** daily reminders for each Sandhya, timed from sunrise and sunset calculated for the user's location.
- **Family:** parents and children can link accounts, either by Google sign-in or by scanning a child's QR code or entering the child's ID and PIN. Cloud Functions send notifications for messages and goals.
- **Backup and sync:** practice records can be backed up and restored through Firebase, with a local-first design so practice still works offline.
- **Mantras as data:** each mantra is a validated JSON definition in the domain module. See [adding a mantra](docs/adding-a-mantra.md).

## Short description

> Android app for Vedic mantra practice: guided recitation with on-device pronunciation scoring, japa and sankalpa tracking, family progress, and Sandhya reminders.

## Tech stack

### Android app (`frontend/android`, Gradle module `:app`)

| Area | Technology | Version |
| --- | --- | --- |
| Language | Kotlin | 1.9.24 |
| Annotation processing | KSP | 1.9.24-1.0.20 |
| Build | Android Gradle Plugin | 8.13.2 |
| Build | Gradle wrapper | 8.13 |
| Android platform | `compileSdk` / `targetSdk` / `minSdk` | 36 / 36 / 33 |
| JDK | Temurin / JDK 17 (CI uses 17 for Android, 21 for Firebase emulators) | 17 |
| UI | Jetpack Compose BOM, Material 3 | 2024.06.00, 1.2.1 |
| UI | Material Components for Android | 1.12.0 |
| Navigation | Navigation Compose | 2.7.7 |
| Lifecycle | Lifecycle (runtime, ViewModel Compose), Activity Compose | 2.8.3, 1.9.0 |
| Images | Coil (Compose) | 2.6.0 |
| Dependency injection | Dagger Hilt, Hilt Navigation Compose | 2.51.1, 1.2.0 |
| Concurrency | Kotlin Coroutines | 1.8.1 |
| Local database | Room (runtime, KTX, compiler) | 2.6.1 |
| Preferences | Jetpack DataStore Preferences | 1.1.7 |
| Mantra catalog | kotlinx.serialization JSON | 1.6.3 |
| Authentication | Firebase Authentication (email/password and Google), Credential Manager, Google ID | Firebase BoM 33.5.1, 1.3.0, 1.1.1 |
| Cloud data | Cloud Firestore, Firebase Cloud Messaging | Firebase BoM 33.5.1 |
| App integrity | Firebase App Check (Play Integrity) | Firebase BoM 33.5.1 |
| Audio and speech | `AudioRecord`, Android `SpeechRecognizer` (on-device), custom FFT and mel filter bank | Platform APIs |
| QR linking | Google code scanner, ZXing core | 16.1.0, 3.5.3 |
| Unit tests | JUnit 4, JUnit 5 (Jupiter), MockK | 4.13.2, 5.10.2, 1.13.10 |
| Instrumented tests | Espresso, AndroidX Test (JUnit ext), Compose UI test | 3.7.0, 1.3.0, BOM |
| Performance | Macrobenchmark module with baseline profiles (`macrobenchmark`) | Compose BOM |

### Domain and data modules (`core/`)

- **`core/domain`:** pure Kotlin, with no Android, Firebase, or UI dependencies. Holds models, repository contracts, use cases, the mantra evaluator, the mantra catalog (`src/main/resources/mantras`), and solar calculations.
- **`core/data`:** Android integrations: Room, DataStore, Firebase clients (Auth, Firestore, Messaging), audio capture, speech recognition, notifications, and reminder scheduling. Uses Hilt for dependency injection.
- **`core/resources`:** shared strings, notification artwork, reference audio, and benchmark timestamps.

### Backend (`backend/firebase`)

| Area | Technology | Version |
| --- | --- | --- |
| Security rules | Cloud Firestore rules | Deployed with `firebase.json` |
| Cloud Functions | Node.js runtime, `firebase-functions`, `firebase-admin` | Node 22, 6.1.x, 12.6.x |
| Rules and emulator tests | Firebase tools, `@firebase/rules-unit-testing`, `firebase` JS SDK | 14.12.0, 4.0.1, 11.10.0 |
| Test runner | Node.js built-in test runner, Firebase Emulator Suite (Firestore, Auth) | Node 22 |

### Tooling and CI

- GitHub Actions for Android build and unit tests, Firestore rules and performance tests, production dependency audit, and secret scanning with gitleaks (`.github/workflows/security.yml`).
- Dependabot for Gradle, npm, and GitHub Actions dependencies (`.github/dependabot.yml`).
- PowerShell scripts for signed release builds (`scripts/Invoke-SignedRelease.ps1`).

## Repository layout

| Directory | Responsibility |
| --- | --- |
| `frontend/android` | Android application (`:app`): Compose screens, UI state, ViewModels, navigation, theme, artwork, and fonts. |
| `core/domain` | Pure Kotlin models, repository contracts, use cases, mantra evaluation, the mantra catalog, and solar calculations. |
| `core/data` | Room, DataStore, Firebase clients, audio, speech recognition, notifications, reminder scheduling, and dependency injection. |
| `core/resources` | Shared strings, notification artwork, reference audio, and benchmark timestamps. |
| `backend/firebase` | Firestore security rules and the optional notification Cloud Function, deployed independently of the app. |
| `macrobenchmark` | Macrobenchmark and baseline profile generation. |
| `docs` | Architecture, design decisions, security notes, asset provenance, licenses, and UI review screenshots. |

The app calls the domain use cases and data services. The domain defines the contracts, and the data module implements them. Scoring and offline storage run on the device. See [architecture](docs/architecture.md) for details.

## Getting started

### Prerequisites

- JDK 17 (or a compatible newer JDK)
- Android SDK 36, with build-tools 35.0.0
- Android Studio (optional, for the IDE)
- A Firebase project with the Android app configured (`frontend/android/google-services.json`)

Set `sdk.dir` in your local, untracked `local.properties`, as usual for Android projects.

### Build

Windows (PowerShell):

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `frontend/android/build/outputs/apk/debug/app-debug.apk`.

### Test and lint

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

The first command runs the unit tests. The last one requires a connected device or emulator.

### Firebase backend

The Firestore rules and Cloud Functions live in `backend/firebase`. Their tests run against the Firebase Emulator Suite:

```bash
cd backend/firebase
npm ci
npm ci --prefix functions
npm test
npm run test:perf
```

Deploying the rules or functions is a separate step and does not change the app build.

## Adding a mantra

Add `core/domain/src/main/resources/mantras/<id>.json`, list it in `catalog.json`, and point `defaultMantraId` at it if it should be the mantra the app teaches. No Kotlin changes are needed. The field-by-field guide, including optional reference audio and dictation details, is in [docs/adding-a-mantra.md](docs/adding-a-mantra.md).

## Release signing

Release builds read their signing credentials from process environment variables, and the keystore must live outside the repository:

| Variable | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_PATH` | Absolute path to an existing keystore outside the project tree |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Upload key alias |
| `ANDROID_KEY_PASSWORD` | Key password |
| `ANDROID_REQUIRE_RELEASE_SIGNING` | Set to `true` for publishable builds |

Inject these from your OS vault or CI secret manager. Do not commit keystores, `keystore.properties`, or credential files. The helper in `scripts/Invoke-SignedRelease.ps1` loads the credentials into the process environment only for the build. See [signing setup and build security](docs/security/06-implementation.md).

## Documentation

- [Architecture](docs/architecture.md) and [design decisions](docs/design-decisions.md)
- [Adding a mantra](docs/adding-a-mantra.md)
- [Verification results](docs/verification.md)
- [Security](docs/security/README.md)
- [UI review screenshots](docs/ui-review/README.md)
- [Artwork prompts and provenance](docs/artwork.md)

### UI review build

The debug build includes an offline review activity that uses production components and sample data. It is absent from release builds and has no launcher entry. To open a page on an emulator:

```powershell
adb shell am start -n shastralife.anushtanam/com.mantrasiddhi.review.DesignReviewActivity --es page home --ez dark false
```

Available pages: `welcome`, `parent-auth`, `child-auth`, `child-id`, `home`, `settings`, `progress`, `practice`, `recitation`, `targets`, `set-target`, `family`, `children`, `sandhya`, `purashcharana`, `voice`, and `empty`.

## License

No license has been chosen yet. Until one is added, all rights are reserved by the author. Bundled third-party fonts keep their own licenses in [docs/licenses](docs/licenses).
