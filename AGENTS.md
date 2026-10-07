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
- Code/resource tasks normally increment `versionCode` exactly once.
- Exception: changes made in other than `main` branches do not increment `versionCode`. Other branches follow the `versionCode` inherited from `main`; open-alpha-specific UI, feedback infrastructure, documentation, and other branch-only changes must not advance it.
- When `main` is updated and those changes are incorporated into `open-alpha`, keep the `versionCode` inherited from the updated `main`.
- Do not change `versionName` unless the task explicitly requires a version change.
- Verification-only builds that make no project changes must not increment `versionCode`.
- The final `./gradlew.bat :app:assembleDebug` must use the incremented `versionCode`.
- After finishing task tell developer current application version from `versionName` and `versionCode`.
- In confirm/cancel pairs, place Cancel on the left and the confirmation on the right in new dialogs, floating panels and other paired actions. This puts the primary action closer to and more convenient for a right-handed user.
- Actions usually use pill-style controls; selection/selectable options use rounded rectangles. Within one compact panel, consistent styling of same-level elements takes precedence. Custom List action dialogs use pills; membership dialogs and Training selections use rounded rectangles. Kanji Details intentionally uses matching pills for Learning/Known/Custom Lists.

## Codex workflow

- Inspecting and reading project files does not require confirmation.
- Before substantial code or architecture changes, present a concise implementation plan and wait for explicit approval.
- After approval, proceed within the agreed scope without requesting additional confirmation for routine file reads, edits, builds, or tests.
- Small local fixes may be applied directly unless the user explicitly asks for a plan first.
- Do not commit or push unless explicitly requested.
- Do not modify files outside the KanjiDB project directory, install system-wide software, change OS/user settings, credentials, environment configuration, or global Git/Codex configuration unless explicitly requested.