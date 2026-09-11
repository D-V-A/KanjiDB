# KanjiDB

Android application written in Kotlin with Jetpack Compose.

## Project goals

KanjiDB is an offline-first Japanese kanji reference and training application.

Planned core features:
- Search by kanji, kana, or romaji
- Kanji details: meanings, on'yomi, kun'yomi, example words, stroke order
- Personal kanji states and custom lists
- Training mode based on writing answers on paper
- Local dictionary data
- No backend or account required for MVP

## Technical rules

- Kotlin
- Jetpack Compose
- Minimum SDK 26
- Keep architecture simple
- Do not introduce dependency injection frameworks unless explicitly requested
- Do not add unnecessary modules or abstractions
- Prefer AndroidX / Jetpack components
- Use Room for user-owned persistent data
- Dictionary data will eventually come from a prebuilt local SQLite database
- The app should work offline
- Do not modify unrelated files
- Preserve existing functionality unless explicitly asked to change it
- Build the project after significant changes
- Fix compilation errors before considering a task complete
- Do not commit or push unless explicitly asked

## Workflow

- Inspect existing code before large changes
- Explain significant architectural decisions
- Make changes in small, reviewable steps