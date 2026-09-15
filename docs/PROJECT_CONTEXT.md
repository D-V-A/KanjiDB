# Project context

## Purpose and current state

KanjiDB is an offline-first Android kanji reference and future training app, with no backend or account required for MVP. Training is intended to use answers written on paper.

Current version: **0.1.9-alpha**, `versionCode = 3`. Both values are set manually in [app/build.gradle.kts](../app/build.gradle.kts); there is no automatic derivation from Git or build date. About reads the installed package's versionName through PackageManager. versionName is the display version; versionCode is the Android update sequence and should increase for subsequent distributed updates. Neither currently versions the dictionary.

Implemented in v0.1 alpha: bundled offline dictionary access, Search Kanji/Words, refreshable Explore Kanji/Words, linked Kanji/Word Details, common-first word lists with written-form deduplication, About with version and basic source credits, and bottom navigation. My Kanji has a working mock collection UI. Training, Recommended Kanji, list assignment and stroke order remain unfinished placeholders; My Lists and Kanji Groups are disabled tabs.

## Stack and code map

Single `:app` module: Kotlin, Jetpack Compose/Material 3, Navigation Compose, coroutines and Android SQLiteDatabase. Minimum SDK 26; compile/target SDK 37; Java compatibility 11. Gradle/plugin/dependency versions live in the build files and [version catalog](../gradle/libs.versions.toml).

- [MainActivity](../app/src/main/java/com/example/kanjidb/MainActivity.kt) hosts the Compose theme and app.
- [KanjiDbApp](../app/src/main/java/com/example/kanjidb/ui/KanjiDbApp.kt) owns navigation: Search, My Kanji, Training, About and detail routes.
- [DictionaryDatabase](../app/src/main/java/com/example/kanjidb/data/dictionary/DictionaryDatabase.kt) owns dictionary access, shared detail/word models and word grouping.
- [KanjiSearch](../app/src/main/java/com/example/kanjidb/data/dictionary/KanjiSearch.kt) and [WordSearch](../app/src/main/java/com/example/kanjidb/data/dictionary/WordSearch.kt) contain search/explore SQL; reading normalization is shared.
- Screens under `ui/` call the dictionary directly using Compose effects; database work runs on Dispatchers.IO. There is no ViewModel/repository/DI layer to extend by assumption.

Room is the chosen future store for user-owned data, but is not yet a dependency or implementation. Kanji Details still uses independent screen-local rememberSaveable Known/Learning state. My Kanji uses an in-memory mock collection owned by the app composition, retained across navigation but reset when that composition is recreated; neither is a persistent personal collection, and the two are not synchronized.

## Dictionary and import pipeline

Sources configured in [download_dictionary_sources.py](../tools/download_dictionary_sources.py): EDRDG KANJIDIC2 and **JMdict_e** (English edition). KANJIDIC2 supplies characters, meanings, Japanese on/kun readings, stroke count, grade and frequency. Jōyō is derived from grades 1–6 or 8. KanjiVG is not integrated.

The Python/SQLite pipeline is run explicitly, separately from Gradle. Commands and order are in [tools/README.md](../tools/README.md): download → import KANJIDIC2 → import JMdict → verify. Source XML lives under `tools/data/`; output is `app/src/main/assets/dictionary.db`. Both are Git-ignored, so a fresh checkout needs the asset supplied or generated. The downloader skips existing XML; rerunning it is not a source-update mechanism.

KANJIDIC2 import rebuilds the dictionary, removing the JMdict tables too. JMdict import rebuilds only its own tables. Do not rerun imports as a routine documentation/UI check.

Schema overview:

- `kanji` with child `kanji_meaning` and `kanji_reading`.
- `word_form`: valid (JMdict entry_id, written, reading) combinations, common flag and preferred-reading metadata.
- `word_meaning`: glosses by form, sense and language.
- `word_kanji`: links forms to known kanji with their positions.

JMdict reading/writing and sense restrictions are respected. The importer currently excludes entries without written forms, forms without a KANJIDIC2 character, and re_nokanji readings. Thus Search is complete over the imported data, **not all JMdict**. Language-aware storage does not imply that the English JMdict source provides other translations.

At runtime the asset is copied once through a temporary file into noBackupFilesDir, then opened with OPEN_READONLY. The reproducible copy is excluded from backup. Existing copies are reused without version checks: replacing the APK asset does not replace an installed dictionary. There are no runtime schema migrations or dictionary-update mechanism.

SQL avoids window functions for SDK 26 compatibility. Internal SQLite IDs can change on rebuild; do not assume they are stable identifiers for future user data.

## Search and discovery

**The full dictionary and the kanji automatically offered by the app are different sets.** Search must remain as complete as possible. Explore/Recommendations must use sufficiently confirmed Japanese kanji; do not apply discovery eligibility filters to Search or delete dictionary entries to curate suggestions.

[SearchScreen](../app/src/main/java/com/example/kanjidb/ui/search/SearchScreen.kt) switches between Kanji and Words. Nonblank queries debounce for 250 ms, start with 10 results and expand by 10 via Show more. Loading/error/empty states and retry are present.

- **Search Kanji:** exact character, kana/romaji readings and English meaning substrings. Exact character ranks first, then exact reading/meaning, then partial matches; frequency breaks ties. Reading normalization handles kana variants, common romaji and dictionary reading punctuation.
- **Search Words:** written-form, kana/romaji reading and English gloss matches. Exact written form ranks first, exact reading/meaning next, then partial matches. Results are unique by (entry_id, written); one preferred reading is selected. Search includes non-common forms and is not frequency-ranked by common.
- **Explore Kanji:** five random characters with an English meaning and on/kun reading, plus at least one of Jōyō status, frequency or a JMdict word link. This is the current evidence-based eligibility heuristic.
- **Explore Words:** five random distinct entries having common=1; one common form/reading represents each entry.
- Blank search shows Explore Kanji / Recommended Kanji / Explore Words pages. Recommended Kanji is a placeholder with a disabled tab.
- [ExploreState](../app/src/main/java/com/example/kanjidb/ui/search/ExploreState.kt) retains both selections for the process lifetime, including navigation and Activity recreation. Refresh is explicit; process restart resets selections. This is not a daily or personalized recommendation system.

## Details and word semantics

Standalone English kanji meanings in Details and Search/Explore capitalize only the first letter, preserving the rest of the source text. Dictionary text in both detail screens and Search/Explore/related-word rows supports standard selection and copying in individual text blocks; interface controls remain outside selection.

**Kanji Details** shows English meanings, on/kun readings, glyph, stroke count, grade, frequency and a conditional Jōyō badge. Related words open Word Details. The stroke view is a placeholder; Add to list is disabled.

**Common/non-common:** import marks a form common=1 if either its writing or reading has any JMdict priority tag. This is a broad heuristic, not a complete frequency ranking. reading_priority is a separate local heuristic from reading priority tags, with original reading order and ID as tie-breakers.

Kanji Details merges common forms first, then remaining forms, unique by (entry_id, written), retaining the common preferred reading. Both blocks use written-form/entry ordering. After deduplication, the UI shows the first six words and Show more/less expands/collapses the whole list. Six is a display limit, not the boundary between common and non-common. All cards currently receive the same recommended styling; personalized word recommendations do not exist.

**Written-form deduplication:** in the kanji-related list, forms can share a representative only within the same entry_id and selected reading, when normalized English meaning sets overlap by at least 75% of the larger set. Comparison is against retained representatives, not transitive clustering; the first form wins. Different entries/readings stay separate. Despite the helper names groupCommonWords/deduplicateCommonWords, grouping runs on the merged common and non-common list. Search uses its own exact (entry_id, written) grouping, not this similarity rule.

**Word Details** identifies a word by entry_id and written, with optional sourceKanji. From Kanji Details, that context reconstructs the deduplication group, exposing alternative written forms and their readings while preserving the representative's preferred reading. From Search/Explore, only the selected written form is used. Meanings are deduplicated and grouped by language for the selected written form, not merged from every alternative.

## My Kanji (mock UI)

My Kanji contains Learning and Known sections, initially collapsed, with live counts, sticky collapsible headers and compact adaptive square-card grids. Each starts with thirty real dictionary characters; cards show the first kun reading, then the first on reading as fallback, or a dash when neither exists. Normal taps open the existing Kanji Details.

Long press selects a card and enters section-specific multi-selection. The other section is hidden without changing either expansion flag. Deselecting every card keeps selection mode active. Cancel, system Back or a long press on empty content space exits selection; mock Move/Remove also exit and update counts without duplicates. A single-row outlined action panel sits above bottom navigation and shares its container with Kanji Details. On selection entry, the grid scrolls only as needed to reveal the initial card above the measured panel; Training is disabled. Mock changes are never written to storage. The persistent My Kanji milestone remains open.

## Verification and next work

Existing focused JVM tests cover kanji search/normalization and common-word merge/grouping under `app/src/test/.../data/dictionary/`. The Python verifier checks the generated dictionary. Run the debug build after significant implementation changes as specified in [AGENTS.md](../AGENTS.md).

Future work and unresolved checks are maintained only in [TODO.md](TODO.md). About currently names KANJIDIC2/JMdict and EDRDG; this is not confirmation that attribution/license requirements have been fully verified.
