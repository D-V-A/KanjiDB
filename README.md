# KanjiDB

**KanjiDB** is an offline-first Android application for studying Japanese kanji and vocabulary.

It combines a Japanese dictionary, kanji study collections, recommendations and recall-based training in a single app.
No account, backend or permanent internet connection is required.

> **Status: Pre-release**
>
> KanjiDB has entered the pre-release stage.
> The core features are implemented and usable, while development is now focused on polish, bug fixes, data quality and preparation for the first stable release.

## Main features

- Offline kanji and Japanese vocabulary dictionary
- Search by kanji, kana, romaji and English meaning
- Detailed kanji pages with readings, meanings, frequency data, related vocabulary and animated stroke order
- Detailed word pages with writings, readings and meanings
- Personal kanji collection with Learning and Known states
- Custom study lists
- "Learn Next" recommendations based on your current progress
- Kanji groups by JLPT level, school grade, frequency and stroke count
- Two Training modes for practicing kanji: Kanji Training and Word Training
- Training result review and mistake repetition
- Fully offline normal use

## Dictionary and search

KanjiDB includes more than **13,000 kanji** and more than **150,000 Japanese vocabulary entries**.

Search supports:

- kanji;
- kana;
- romaji;
- English meanings.

Kanji and word results are connected, so you can move between a kanji and vocabulary containing it without losing your place.

## Kanji Details

Kanji pages include:

- English meanings;
- on'yomi and kun'yomi readings;
- stroke count;
- JLPT level;
- school grade and Jōyō information when applicable;
- frequency information;
- related vocabulary;
- animated stroke order where KanjiVG data is available.

Stroke animations show individual strokes in writing order and replay automatically for convenient study.

## Word Details

Word pages include the written form, applicable readings and meanings, alternative writings where available, and the kanji used by the word.

Kanji inside a word can be opened directly, and navigation between kanji and words can continue recursively without losing the original screen state.

## My Kanji and Lists

Kanji can be saved as either **Learning** or **Known**.

Your collection can be sorted, grouped and filtered, and multiple kanji can be selected for bulk actions or training.

You can also create your own lists, add kanji to multiple lists, reorder their contents and start training directly from a list.

## Learn Next

KanjiDB can suggest kanji to learn next based on your current progress.

The current recommendation system uses JLPT-oriented data together with your Learning and Known kanji.

## Kanji Groups

The dictionary can be explored using groups based on properties such as JLPT level, school grade, frequency and stroke count.

Rules can be combined to narrow the result set, after which kanji can be saved or used directly for training.

## Training

KanjiDB currently includes two training modes.

### Kanji Training

Practice recalling kanji meanings, on'yomi readings and kun'yomi readings.

The answer is revealed when you are ready, and you mark the result yourself.

On-screen drawing is planned; for now it's still **you-drawing** — on a good old sheet of paper.

### Word Training

Practice kanji in the context of real Japanese vocabulary.

Sessions can test:

- translation;
- reading;
- both translation and reading.

Word selection is based on the kanji being trained rather than simply choosing random vocabulary.

After a session, results can be reviewed and you can repeat the entire session or focus on mistakes.

## Offline-first

KanjiDB is designed to work without an account or online service.

The Japanese dictionary is bundled with the application. Personal data such as Learning/Known states and lists is stored locally on the device.

Dictionary search, browsing, stroke order and training do not require an internet connection.

## Pre-release status

The 0.9.x series represents the current pre-release stage of KanjiDB.

The main functionality is already available for normal use, but bugs, UI changes and smaller behavioral changes may still occur before the first stable release.

Feedback and bug reports are very welcome. The application includes links to the project's feedback form and GitHub repository.

## Download

Pre-built Android APKs are available from the repository's **Releases** section.

KanjiDB supports **Android 8.0 (API 26) and newer**.

Application takes 300 MB of device's memory after first launch.

## Dictionary and third-party sources

KanjiDB uses data derived from:

- **KANJIDIC2** for kanji information;
- **JMdict** for Japanese vocabulary;
- **KanjiVG** for kanji stroke-order data;
- additional JLPT mapping data.

The application also uses **Tabler Icons**, distributed under the MIT License.

Third-party attribution and licensing information is included with the application.

## Development

KanjiDB is built with Kotlin, Jetpack Compose, Material 3, Room and SQLite.

The project is developed publicly on GitHub.

Development is currently focused on stabilizing the existing feature set and preparing for the first stable release.

Future ideas include personal word collections, recommended vocabulary, handwriting practice, deeper component/radical data, pronunciation/TTS, Kanji of the Day and additional study options.

The roadmap may change based on testing and feedback.

## License

The application license and complete third-party attribution information will be finalized before a stable release.
