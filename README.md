# Anushtanam

A native Android app for Vedic mantra practice, japa, sankalpa, family progress, and Sandhya reminders. It ships with the Gayatri; the mantras it can teach are JSON definitions, not code — see [adding a mantra](docs/adding-a-mantra.md).

## Where to work

| Directory | Responsibility |
| --- | --- |
| `frontend/android` | Android application, Compose screens, UI state, ViewModels, navigation, theme, artwork, and fonts. Gradle name remains `:app`. |
| `core/domain` | Pure Kotlin models, repository contracts, use cases, mantra evaluation, the mantra catalog (`src/main/resources/mantras`), and solar calculations. No Android, Firebase, or UI dependencies. |
| `core/data` | Room, DataStore, Firebase clients, audio, speech recognition, notifications, reminder scheduling, and dependency injection. |
| `core/resources` | Shared strings, notification artwork, reference audio, and benchmark timestamps. |
| `backend/firebase` | Deployed Firestore rules and the optional notification Cloud Function. |
| `docs` | Architecture, design decisions, asset provenance, licenses, and UI review screenshots. |

The frontend calls existing use cases and data services. The domain defines contracts; the data module implements them. The Firebase backend is deployed independently. Local evaluation and offline storage still run on the device.

## Adding a mantra

Add `core/domain/src/main/resources/mantras/<id>.json`, list it in `catalog.json`, and you are done — no Kotlin changes. Point `defaultMantraId` at it to make it the mantra the app teaches. The full field-by-field guide, including optional reference audio and dictation quirks, is in [docs/adding-a-mantra.md](docs/adding-a-mantra.md).

## Build and check

Use JDK 17 or a compatible newer JDK, Android SDK 36, and the included Gradle wrapper. Set `sdk.dir` in your untracked `local.properties` as usual.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

The debug APK is `frontend/android/build/outputs/apk/debug/app-debug.apk`. The application ID, signing configuration and database name are unchanged. The Room schema is at v10. The v9 to v10 migration adds account ownership and photo preferences without removing saved practice. Release signing still reads the untracked root `keystore.properties`.

MF-07 adds Google sign-in, account linking and profile photos. The refreshed Firebase configuration includes the Google OAuth client. See [MF-07 setup and verification](docs/mentor-feedback/mf-07-implementation.md) for the remaining live account checks.

Children can also use their own Google account. Parents can connect by scanning a child's QR code or entering their ID and PIN. See [child sign-in and family linking setup](docs/mentor-feedback/child-google-family-linking.md), including the required Firestore rules update.

## UI review

The debug build includes an offline review activity using the production components and sample data. It is absent from release builds and has no launcher entry. Open a page on an emulator:

```powershell
adb shell am start -n shastralife.anushtanam/com.mantrasiddhi.review.DesignReviewActivity --es page home --ez dark false
```

Pages: `welcome`, `parent-auth`, `child-auth`, `child-id`, `home`, `settings`, `progress`, `practice`, `recitation`, `targets`, `set-target`, `family`, `children`, `sandhya`, and `purashcharana`, `voice`, and `empty`. Authentication pages expose the existing forms; use them only for visual inspection unless you intend to sign in. Review home and settings use local fixtures.

See [adding a mantra](docs/adding-a-mantra.md), [the screenshot gallery](docs/ui-review/README.md), [verification results](docs/verification.md), [architecture](docs/architecture.md), [design decisions](docs/design-decisions.md), and [artwork prompts and provenance](docs/artwork.md).
