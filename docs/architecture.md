MF-08 update (10 September 2026): [automatic practice recovery](mentor-feedback/mf-08-implementation.md) adds full saved performance and history, resumable restore checkpoints, conflict review, and a status card above Sign out. It supersedes the earlier aggregate-only backup and login-restore descriptions below.

# Source architecture

The `:app` Gradle name is preserved to keep familiar build commands working. Its physical source directory is `frontend/android`. All original package names remain stable except `SyncSignedInUserUseCase`, which lives in `com.mantrasiddhi.data.session` because it orchestrates concrete DataStore implementations.

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
- For the mantras themselves, use `core/domain/src/main/kotlin/com/mantrasiddhi/domain/mantra` and the JSON definitions beside it in `core/domain/src/main/resources/mantras`.
- For persistence and synchronization, use the data module's `local`, `preferences`, `repository`, and `remote` folders. Domain repository interfaces define the data contracts.
- For Cloud Functions or security rules, use `backend/firebase`. Moving these files does not deploy them or change the running service.

## The mantra catalog

Mantras are **data, not code**. Each one is a JSON definition on the domain module's classpath, read once by `BundledMantraCatalog` and validated by `MantraCatalogReader`. Adding a mantra is adding a file; see [adding-a-mantra.md](adding-a-mantra.md).

```text
core/domain/src/main/resources/mantras/
  ├── catalog.json     which definitions to load, and which one the app teaches
  └── gayatri.json     one mantra: words, padas, aksharas, benchmark, dictation quirks
```

Nothing downstream holds a mantra of its own:

- `MantraEvaluator`, `PronunciationScorer` and `ImprovementSuggestionEngine` take the `Mantra` they are scoring against.
- `TextNormalizer` and `PhoneticMap` keep only what is true of Sanskrit in Latin letters; anything true of one mantra arrives as that mantra's `MantraTextRules`.
- Use cases inject `@ActiveMantra Mantra`, bound in `core/data`'s `MantraModule` to `MantraCatalog.default`. That binding is the single place the app decides which mantra it is teaching, and the only thing that would change if the sadhak were ever given the choice.
- `AcousticAnalyzer` and `SwaraAnalyzer` take the mantra too; their caches (`BenchmarkFeatureCache`, `BenchmarkPitchCache`) are memoised per mantra id, so several mantras can coexist without one's audio windows leaking into another's.
- Recitation sessions and Purashcharana vows persist a `mantraId`. Per-word analytics (mastery, weak words, the practice plan) filter on it, because a per-word score is only meaningful inside the mantra it came from.

`com.mantrasiddhi.domain.evaluation.reference.GayatriMantra` survives as a deprecated static view of the catalog's default mantra. It exists so the presentation layer keeps compiling unchanged; those call sites should take a `Mantra` when the UI is next touched, after which the object can go.

## Preserved contracts

The application ID is `shastralife.anushtanam`. Room's exported schemas live in `core/data/schemas`. Shared audio and benchmark timestamps live in `core/resources`. The merged app still exposes the same resource names. Resource imports use `com.mantrasiddhi.resources.R`; frontend-only artwork and fonts use the app's `com.mantrasiddhi.R`.

Authentication, scoring, counting, rewards, reminder scheduling, synchronization, and preference storage are unchanged in behaviour.

**Database v7** adds a `mantraId` column to `recitation_sessions` and `purashcharana`. The migration is purely additive: `ALTER TABLE ... ADD COLUMN mantraId TEXT NOT NULL DEFAULT 'gayatri'` on each, which backfills every existing row with the mantra the app shipped with. The entities declare the same `@ColumnInfo(defaultValue = ...)`, so a fresh install and an upgraded one produce identical DDL. The Firestore `purashcharana/{uid}` mirror gains a matching `mantraId` field; documents written before it are read as the Gayatri, so an existing sadhak's sankalpa still reconciles after the upgrade.

## Tests

Domain tests live with the domain sources, data and migration tests with the data module, and ViewModel tests in the frontend. `LotusUiTest` covers UI callbacks and accessible control states. The debug review activity provides fixtures for visual checks without seeding Room or creating Firebase records.

Two tests are worth knowing about when working on mantras:

- `BundledMantraCatalogTest` loads every shipped definition and pins the Gayatri's words, padas, akshara count, syllables and benchmark timings to exactly what they were when the reference text lived in Kotlin.
- `SecondMantraTest` defines a mantra inline and drives it through evaluation, feedback and the practice plan. It is the executable form of the claim that adding a mantra needs no code.
