# Project context

## Purpose and current state

KanjiDB is an offline-first Android kanji reference and future training app, with no backend or account required for MVP. Training is intended to use answers written on paper.

Current version: **0.3.1-alpha**, `versionCode = 6`. Both values are set manually in [app/build.gradle.kts](../app/build.gradle.kts); there is no automatic derivation from Git or build date. About reads the installed package's versionName through PackageManager. versionName is the display version; versionCode is the Android update sequence and should increase for subsequent distributed updates. Neither currently versions the dictionary.

Implemented in v0.3 alpha: bundled offline dictionary access, Search Kanji/Words, refreshable Explore Kanji/Words, linked Kanji/Word Details, common-first word lists with written-form deduplication, About with version and basic source credits, and bottom navigation. My Kanji and Kanji Details share persistent Learning/Known states. My Kanji displays real user data from the separate Room user.db, with no mock collections. Kanji Groups supports dictionary browsing, rules and bulk state assignment. My Kanji / My Lists / Kanji Groups are enabled tabs in a shared swipe pager; My Lists remains a placeholder. Training, Recommended Kanji, list assignment and stroke order remain unfinished placeholders.

## Stack and code map

Single `:app` module: Kotlin, Jetpack Compose/Material 3, Navigation Compose, coroutines and Android SQLiteDatabase. Minimum SDK 26; compile/target SDK 37; Java compatibility 11. Gradle/plugin/dependency versions live in the build files and [version catalog](../gradle/libs.versions.toml).

- [MainActivity](../app/src/main/java/com/example/kanjidb/MainActivity.kt) hosts the Compose theme and app.
- [KanjiDbApp](../app/src/main/java/com/example/kanjidb/ui/KanjiDbApp.kt) owns navigation: Search, My Kanji, Training, About and detail routes.
- [DictionaryDatabase](../app/src/main/java/com/example/kanjidb/data/dictionary/DictionaryDatabase.kt) owns dictionary access, shared detail/word models and word grouping.
- [KanjiSearch](../app/src/main/java/com/example/kanjidb/data/dictionary/KanjiSearch.kt) and [WordSearch](../app/src/main/java/com/example/kanjidb/data/dictionary/WordSearch.kt) contain search/explore SQL; reading normalization is shared.
- Screens under `ui/` call the dictionary directly using Compose effects; database work runs on Dispatchers.IO. There is no ViewModel/repository/DI layer to extend by assumption.

User-owned kanji state is stored in a separate Room `user.db` (schema version 1), independent of the read-only dictionary. `UserKanjiStateEntity` stores only the Unicode character primary key and LEARNING/KNOWN; an absent row means NONE. Dictionary IDs and content are never copied into user storage. The database starts empty, has no demo seeding or destructive migration fallback, and exports its schema to `app/schemas`. `UserKanjiStateDao` exposes Flow observations and transactional toggle/bulk assignment/removal. Screens collect with lifecycle awareness; suspend DAO calls run asynchronously. There is no repository or DI layer.

## Dictionary and import pipeline

Sources configured in [download_dictionary_sources.py](../tools/download_dictionary_sources.py): EDRDG KANJIDIC2 and **JMdict_e** (English edition). KANJIDIC2 supplies characters, meanings, Japanese on/kun readings, stroke count, grade and frequency. Jōyō is derived from grades 1–6 or 8. KanjiVG is not integrated.

The Python/SQLite pipeline is run explicitly, separately from Gradle. Commands and order are in [tools/README.md](../tools/README.md): download → import KANJIDIC2 → import JMdict → verify. Source XML lives under `tools/data/`; output is `app/src/main/assets/dictionary.db`. Both are Git-ignored, so a fresh checkout needs the asset supplied or generated. The downloader skips existing XML; rerunning it is not a source-update mechanism.

KANJIDIC2 import rebuilds the dictionary, removing the JMdict tables too. JMdict import rebuilds only its own tables. Do not rerun imports as a routine documentation/UI check.

Schema overview:

- `kanji` with child `kanji_meaning` and `kanji_reading`.
- `jlpt_kanji`: optional level 1-5 linked by `kanji_id`, read directly by Kanji Groups; never copied into user.db.
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

**Kanji Details** shows English meanings, on/kun readings, glyph, stroke count, grade, frequency and independent conditional Jōyō and JLPT (N5-N1) badges. JLPT is read from jlpt_kanji and omitted when no mapping exists; it is not repeated as a metadata row. Related words open Word Details. The stroke view is a placeholder; Add to list is disabled.

**Common/non-common:** import marks a form common=1 if either its writing or reading has any JMdict priority tag. This is a broad heuristic, not a complete frequency ranking. reading_priority is a separate local heuristic from reading priority tags, with original reading order and ID as tie-breakers.

Kanji Details merges common forms first, then remaining forms, unique by (entry_id, written), retaining the common preferred reading. Both blocks use written-form/entry ordering. After deduplication, the UI shows the first six words and Show more/less expands/collapses the whole list. Six is a display limit, not the boundary between common and non-common. All cards currently receive the same recommended styling; personalized word recommendations do not exist.

**Written-form deduplication:** in the kanji-related list, forms can share a representative only within the same entry_id and selected reading, when normalized English meaning sets overlap by at least 75% of the larger set. Comparison is against retained representatives, not transitive clustering; the first form wins. Different entries/readings stay separate. Despite the helper names groupCommonWords/deduplicateCommonWords, grouping runs on the merged common and non-common list. Search uses its own exact (entry_id, written) grouping, not this similarity rule.

**Word Details** identifies a word by entry_id and written, with optional sourceKanji. From Kanji Details, that context reconstructs the deduplication group, exposing alternative written forms and their readings while preserving the representative's preferred reading. From Search/Explore, only the selected written form is used. Meanings are deduplicated and grouped by language for the selected written form, not merged from every alternative.

## My Kanji and Kanji Groups

The My Kanji destination contains a HorizontalPager with synchronized tappable tabs: My Kanji, My Lists and Kanji Groups. All three are accessible by swiping; My Lists is still a placeholder. Pager position, group/sort/rule controls, expansion flags and selection keys use saveable Compose state. Each collection has its own destination-owned LazyGridState. Grids are measured only after their real initial data is ready, so loading placeholders cannot overwrite restored scroll positions after returning from Details. No additional navigation destinations, repository or ViewModel layer are introduced.

**My Kanji** contains Learning and Known sections, initially collapsed, with Room-derived counts, sticky collapsible headers and adaptive square-card grids. Both start empty and are populated through Kanji Details or Kanji Groups. Cards show the first kun reading, then the first on reading, or a dash. Missing dictionary entries remain safe to display. Normal taps open Kanji Details. Section-specific selection hides the other section without changing either saved expansion flag. Remove deletes state rows; Move overwrites the selected rows with the other state. Successful actions exit selection and counts update through Room Flow.

**Kanji Groups** uses two bulk dictionary queries for metadata/JLPT and readings, with no per-card detail queries. A lifecycle-aware Room Flow supplies character-to-state mappings separately. Pure Kotlin filtering/grouping/sorting runs on Dispatchers.Default and recomputes on rule or user-state changes. The installed dictionary must contain `jlpt_kanji`; an older cached dictionary without that table produces a recoverable load error. The dictionary copy/update mechanism is unchanged.

- Group by JLPT: N5, N4, N3, N2, N1, No JLPT. Group by Grade: 1, 2, 3, 4, 5, 6, 8, 9, 10, No grade. Reversing complexity reverses the entire group order, including the missing-value group. No grade means only grade IS NULL; No JLPT means no jlpt_kanji row.
- Sort within groups by KANJIDIC2 frequency rank (Frequent first / Rarer first) or strokes (Simpler first / Complex first). Lower frequency rank means more frequent. NULL values stay last in both directions; character is the deterministic tie-breaker. Mixed frequency groups display independently collapsible Ranked and Unranked subgroups, initially expanded, with Ranked always first. Homogeneous groups and Strokes sorting show cards directly. Main-header selection still covers the whole filtered group regardless of this subdivision.
- The Rules button opens a dialog, with a compact single-line active-rule summary beside it (empty when no rules apply). Reset rules inside the dialog clears only rules, preserving grouping and sorting. JLPT, Grade and Joyo each support Only / Not / N/A. Status supports Any / Known / Learning / Known or Learning / Neither. Rules combine freely with AND; empty groups are omitted and an empty result says "No kanji matching filters".
- Add to Learning / Add to Known unconditionally overwrite every selected character via the existing transactional Room bulk API, including kanji already in the destination state. These actions never toggle. Changes update both collection counts and status-filtered groups reactively.

**Shared selection infrastructure:** `KanjiCollectionState` owns entry, toggling, programmatic multi-selection, pruning filtered-out characters, cancellation and saved expansion/selection. `KanjiCollectionGrid` supplies the common kanji card, sticky section header, empty-space long press, active-page Back handling, floating selection panel, and measured autoscroll clearance for the selected card. Header long press selects the whole current filtered section, including items outside the viewport; it never includes filtered-out kanji. Header selection preserves the current viewport without creating a card-reveal request; individual card selection retains minimal autoscroll to clear the floating panel. Pending reveal requests are not restored after navigation/recreation. Groups permits selection across groups. Deselecting all cards keeps selection mode active. Cancel, Back or long press on empty space exits it. Expansion flags survive temporary selection visibility and cancellation.

`KanjiSelectionAction` passes exactly the current eligible selection to an action; `KanjiStateWriter` shares coroutine/bulk-write handling. Training uses the same action model with no handler and stays disabled: future Training must receive that selection without changing saved states. The shared panel continues to use `FloatingActionPanel`, which also supplies the surface for Kanji Details; Details retains its own button layout and persistent toggle semantics.

## Verification and next work

Instrumented tests cover Room toggle/bulk/removal semantics, database reopening, and the existing My Kanji interactions using an isolated test database. JVM tests cover empty initial collections, toggling, shared selection/save-restore, filter/status semantics, group ordering (including grades 8/9/10), stable NULL-last sorting, Ranked/Unranked presentation, semantic direction labels, Rules summary/reset and idempotent bulk overwrite. Device/UI tests require explicit permission and are not part of these JVM checks. Existing focused JVM tests also cover kanji search/normalization and common-word merge/grouping under `app/src/test/.../data/dictionary/`. The Python verifier checks the generated dictionary. Run the debug build after significant implementation changes as specified in [AGENTS.md](../AGENTS.md).

Future work and unresolved checks are maintained only in [TODO.md](TODO.md). About currently names KANJIDIC2/JMdict and EDRDG; this is not confirmation that attribution/license requirements have been fully verified.
