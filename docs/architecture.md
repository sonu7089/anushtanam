# Source architecture

The `:app` Gradle name is preserved to keep familiar build commands working. Its physical source directory is now `frontend/android`. All original package names remain stable except `SyncSignedInUserUseCase`, which now lives in `com.mantrasiddhi.data.session` because it orchestrates concrete DataStore implementations.

```text
frontend/android (:app)
  ├── core/domain (pure Kotlin)
  ├── core/data (Android integrations)
  │     ├── core/domain
  │     └── core/resources
  └── core/resources

backend/firebase (independent server deployment)
```

## Finding code

- For layout or styling, start in `frontend/android/src/main/java/com/mantrasiddhi/presentation`. Each screen has its own folder. `components/LotusDesign.kt` holds shared artwork, cards, app bars, and preference chips; `theme` contains the visual tokens.
- For screen orchestration, use the adjacent ViewModel. ViewModels stay with the frontend because they produce UI state and handle UI events. Business rules stay in domain use cases.
- For mantra scoring, use `core/domain/src/main/kotlin/com/mantrasiddhi/domain/evaluation`. Signal processing and audio acquisition are in `core/data/src/main/java/com/mantrasiddhi/data`.
- For persistence and synchronization, use the data module's `local`, `preferences`, `repository`, and `remote` folders. Domain repository interfaces define the data contracts.
- For Cloud Functions or security rules, use `backend/firebase`. Moving these files does not deploy them or change the running service.

## Preserved contracts

The application ID is `shastralife.anushtanam`. Room's exported schemas now live in `core/data/schemas`; schema contents and database versions have not changed. Shared audio and benchmark timestamps moved to `core/resources` without modification. The merged app still exposes the same resource names. Resource imports use `com.mantrasiddhi.resources.R`; frontend-only artwork and fonts use the app's `com.mantrasiddhi.R`.

Authentication, scoring, counting, rewards, reminder scheduling, synchronization, and preference storage are unchanged. The two cross-module `ChildProgress.activeTarget` reads now use local values so Kotlin can safely narrow the nullable type.

## Tests

Domain tests moved with the domain sources, data and migration tests moved with the data module, and ViewModel tests remain in the frontend. `LotusUiTest` covers UI callbacks and accessible control states. The debug review activity provides fixtures for visual checks without seeding Room or creating Firebase records.
