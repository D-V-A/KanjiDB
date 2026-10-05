# KanjiDB

KanjiDB is an offline-first Android application for studying Japanese kanji and vocabulary.

The project is currently in active development. The current public version is an **alpha**, but the application already provides a complete basic workflow for searching, organizing and training kanji.

## Features

### Dictionary and search

- Search for kanji by character, reading or English meaning.
- Search for Japanese words by written form, reading, romaji or English meaning.
- Detailed kanji information:
  - meanings;
  - on'yomi and kun'yomi readings;
  - stroke count;
  - Jōyō status;
  - JLPT level where available;
  - related vocabulary.
- Detailed word information with readings and meanings.
- Offline dictionary data — no internet connection is required for normal use.

### Kanji collections

- Mark kanji as **Learning** or **Known**.
- Browse saved kanji in **My Kanji**.
- Browse kanji by JLPT level, school grade and other properties.
- Sort and filter collections by frequency, stroke count, JLPT level, grade and other rules.
- Reorder kanji manually where supported.
- Select multiple kanji and apply bulk actions.

### Custom Lists

- Create your own kanji lists.
- Rename, delete and reorder lists.
- Add the same kanji to multiple lists.
- Manage list membership for one or multiple selected kanji.
- Sort and filter kanji inside Custom Lists.
- Use Custom Lists as a source for kanji training.

### Recommended Kanji

KanjiDB can generate a set of recommended kanji based on the user's current progress.

The current recommendation system:

- follows JLPT progression;
- adapts to already Known and Learning kanji;
- prefers more useful/frequent characters while still allowing less common ones to appear;
- avoids immediately repeating recently shown recommendations.

Additional recommendation strategies are planned for future versions.

### Kanji Training

The application includes a recall-based kanji training mode.

Available training sources:

- Review;
- Learning;
- New / Recommended kanji;
- Custom Lists.

A training session includes:

- English meaning;
- on'yomi and kun'yomi readings;
- reveal and self-assessment;
- repeated attempts;
- result review;
- applying Learning / Known status changes at the end of the session.

## Planned features

Current development priorities include:

- training directly from selected kanji;
- stroke order data and visualization;
- handwriting training;
- Japanese pronunciation / TTS;
- Word Training;
- Recommended Words;
- Kanji of the Day;
- additional recommendation strategies;
- global settings;
- further UI and UX improvements.

Longer-term plans also include broader localization and dictionary translation support.

## Offline-first design

KanjiDB is designed to work without a backend or user account.

The main dictionary is bundled with the application and opened locally as a read-only SQLite database.

User-owned data such as Learning/Known states and Custom Lists is stored separately on the device.

This separation allows dictionary data to evolve independently from personal study progress.

## Dictionary sources

KanjiDB currently uses data derived from:

- **KANJIDIC2** for kanji information;
- **JMdict** for vocabulary;
- additional JLPT mapping data.

Attribution and licensing details will be expanded before a stable release.

## Current status

KanjiDB is currently in **alpha**.

The application is usable, but development is ongoing and some behavior, UI and internal data handling may change between versions.

Before installing or upgrading an alpha build, keep in mind:

- bugs are still possible;
- features may be incomplete;
- UI may change significantly;
- backward compatibility between very early versions is not guaranteed.

Recent releases include database migrations intended to preserve existing Learning/Known data across upgrades, but the project should still be considered experimental.

## Download

Pre-built Android APKs are available from the repository's **Releases** section.

## Development

KanjiDB is currently developed for Android using:

- Kotlin;
- Jetpack Compose;
- Material 3;
- Room;
- SQLite.

The project currently targets Android API 26 and newer.

## License

The application license and complete third-party attribution information will be finalized before a stable release.
