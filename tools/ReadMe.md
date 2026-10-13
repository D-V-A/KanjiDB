# Dictionary build

Canonical full build (from the repository root):

```sh
python tools/build_dictionary.py
```

The entry point also works from any working directory when invoked by its path.
It uses the same Python interpreter and stops immediately if a required stage fails,
keeping child stdout/stderr visible. The ordered stages are:

1. Validate the release version.
2. Download KANJIDIC2, JMdict_e and JLPT sources (existing inputs are reused).
3. Import KANJIDIC2 (rebuilds kanji and removes word tables).
4. Import JMdict.
5. Import JLPT with the existing project overrides.
6. Stamp SQLite `PRAGMA user_version` and generate the small version asset.
7. Run the existing dictionary verifier, including version consistency checks.

Outputs are `app/src/main/assets/dictionary.db` and
`app/src/main/assets/dictionary_version.txt`. Both are generated and Git-ignored,
as are source inputs under `tools/data/`; a fresh clone needs a full build before
packaging the APK. No new deduplication, strokes or content model changes are included.

The only manually maintained dictionary version is `tools/dictionary_version.txt`.
It must contain an integer in 1..2147483647. Missing/malformed values fail the build.
Increase it manually only when intentionally releasing new dictionary content or
structure to users. Rebuilds with the same inputs and UI/Kotlin changes do not
increment it. It is independent of Android versionName/versionCode. Never manually
edit the generated asset: the full build writes the same version to SQLite and the
asset, and the verifier compares both against the source of truth.

Individual scripts remain available for development/debugging:

```sh
python tools/download_dictionary_sources.py
python tools/import_kanjidic.py
python tools/import_jmdict.py
python tools/import_jlpt_with_override.py
python tools/verify_dictionary_with_override.py
```

Standalone importers do not stamp release metadata. Use the canonical full build
before distributing a dictionary; the verifier requires consistent release metadata.
The downloader skips existing sources and does not automatically update datasets.
Future import stages can be added to the ordered pipeline before stamping/verifying.
