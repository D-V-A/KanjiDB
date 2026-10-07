# KanjiDB

**KanjiDB** is an offline-first Android application for studying Japanese kanji and vocabulary.

It combines a Japanese dictionary, personal kanji collections and recall-based training in one app.  
No account, backend or permanent internet connection is required.

KanjiDB is currently in **open alpha**. The application is already fully usable for everyday study, but some features and UI may still change as development continues.

## Features

### Search and explore

The built-in dictionary includes:

- More than **13,000 kanji**;
- More than **150,000 words** with almost 250,000 readings.

You can search by:

- Kana;
- Romaji;
- Kanji;
- English meaning.

You can also browse random kanji and words, or use **Recommended Kanji** to discover characters based on your current progress. 
*Recommendations currently use a JLPT-based algorithm that takes your **Learning** and **Known** kanji into account.*

Everything is available offline.

### Kanji and word details

Kanji pages include useful dictionary information such as:

- English meanings;
- on'yomi and kun'yomi readings;
- stroke count;
- JLPT level;
- school grade and Jōyō status where available;
- frequency information;
- related vocabulary.

Word pages show:

- written form;
- readings;
- meanings;
- the kanji used in the word, with quick navigation back to their detail pages.

Kanji and word pages can be followed freely in both directions, making it easy to explore vocabulary and unfamiliar characters without losing your starting point.

### My Kanji

Keep track of the kanji you are studying with two simple states:

- **Learning**
- **Known**

Your collection can be sorted, grouped and filtered in different ways, and manually reordered when appropriate.

Multiple kanji can be selected at once for bulk actions or immediate training.

### Custom Lists

Create your own kanji lists for anything you want to study separately.

You can:

- create, rename and delete lists;
- add kanji to multiple lists;
- manage multiple selected kanji at once;
- sort and filter list contents;
- reorder kanji manually;
- start training directly from a list.

### Kanji Groups

Browse the dictionary as structured groups using properties such as:

- JLPT level;
- school grade;
- frequency;
- stroke count.

Rules can be combined to narrow down the set you are interested in, and selected kanji can be added to your study collection or used for training.

## Training

KanjiDB currently includes two training modes.

### Kanji Training

Practice recalling individual kanji using their:

- meanings;
- on'yomi readings;
- kun'yomi readings.

The answer is revealed when you are ready, and you mark your own result as correct or incorrect.
*On-screen drawing is planned, but for now it's only **you-drawing** — on a good old sheet of paper.*

Training can use your saved kanji, recommended kanji, Custom Lists, or a manually selected group.

### Word Training

Practice kanji in the context of real Japanese words.

Sessions can focus on:

- translation;
- reading;
- both translation and reading.

Word selection takes your chosen kanji into account and tries to build a useful session around them rather than simply picking random vocabulary.

Kanji you already know can appear as context, while unfamiliar characters remain visible so you are not expected to guess material outside the selected pool.

### Results and repetition

After training you can review the results, update kanji states, and practice again using:

- the entire session;
- mistakes only;
- the current iteration.

Word Training results also show which kanji appeared in words you had difficulty with, as a study guide rather than a definitive assessment.

## Offline-first

KanjiDB is designed to work without an account or online service.

The main Japanese dictionary is bundled with the application, while your personal data — including Learning/Known states and Custom Lists — is stored locally on your device.

Normal dictionary use, browsing and training do not require an internet connection.

The application uses about **220 MB of storage** after the first launch and may increase slightly as user data is added.

## Open alpha

KanjiDB is currently under active development.

Open-alpha builds are intended for real-world testing, so you may encounter:

- bugs;
- incomplete features;
- UI changes;
- behavior that changes between versions.

Feedback is very welcome and helps decide what should be improved next.

The open-alpha version includes an in-app feedback link to the project's feedback form and GitHub page.

## Dictionary sources

KanjiDB uses data derived from:

- **KANJIDIC2** for kanji information;
- **JMdict** for Japanese vocabulary;
- additional JLPT mapping data.

The application also uses icons from **Tabler Icons**, distributed under the MIT License.

Third-party attribution and licensing information is included with the application and will continue to be expanded as the project develops.

## Download

Pre-built Android APKs are available from the repository's **Releases** section.

KanjiDB currently supports **Android 8.0 (API 26) and newer**.

## Development

KanjiDB is an Android application built with:

- Kotlin;
- Jetpack Compose;
- Material 3;
- Room;
- SQLite.

The project is developed publicly on GitHub.

## Current status

Current development is focused on improving the existing dictionary and training experience based on open-alpha feedback.

Future ideas include:

- personal word collections;
- recommended vocabulary;
- handwriting practice;
- deeper kanji component/radical data;
- pronunciation / TTS;
- Kanji of the Day;
- additional study and recommendation options.

The exact roadmap may change based on testing and feedback.

## License

The application license and complete third-party attribution information will be finalized before a stable release.
