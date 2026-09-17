# KanjiDB

- Before changing anything, read [project context](docs/PROJECT_CONTEXT.md), consult [plans](docs/TODO.md), and inspect the current implementation.
- Keep the app offline-first; MVP requires no backend or account.
- The dictionary is prebuilt, read-only SQLite. Keep user-owned persistent data in a separate Room database.
- Do not change dictionary schema or import pipeline unless the task requires it.
- Use Kotlin, Jetpack Compose and existing models/logic; do not duplicate them.
- Keep architecture simple, preserve minimum SDK 26, and prefer AndroidX/Jetpack. Do not add DI frameworks unless explicitly requested.
- Preserve existing functionality; avoid unrelated changes, refactors, modules and abstractions.
- Make changes in small, reviewable steps and explain significant architectural decisions.
- After significant changes, run `./gradlew.bat :app:assembleDebug` on Windows (`./gradlew :app:assembleDebug` elsewhere); fix compilation errors before completion.
- Do not commit or push without an explicit request.
- After significant architectural or user-visible changes, update docs/PROJECT_CONTEXT.md and/or docs/TODO.md when the documented state has changed.
- Do not run emulator. And do not run any Device/UI tests without permission. 
- Every task that changes application code or resources must increment the Android `versionCode` by exactly 1 before the final build.
- Increment `versionCode` only once per task/session, not once per Gradle invocation.
- Do not change `versionName` unless the task explicitly requires a version change.
- Verification-only builds that make no project changes must not increment `versionCode`.
- The final `./gradlew.bat :app:assembleDebug` must use the incremented `versionCode`.
- After finishing task tell developer current application version from `versionName` and `versionCode`.