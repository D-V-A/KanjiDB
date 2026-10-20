# Dictionary build

Canonical full build (from the repository root):

```sh
python tools/build_dictionary.py
```

The entry point also works from any working directory when invoked by its path.
It uses the same Python interpreter and stops immediately if a required stage fails,
keeping child stdout/stderr visible. The ordered stages are:

1. Validate the release version.
2. Download KANJIDIC2, JMdict_e and JLPT sources; select/validate the cached KanjiVG main release.
3. Import KANJIDIC2 (rebuilds kanji and removes word tables).
4. Import JMdict.
5. Import JLPT with the existing project overrides.
6. Enrich existing kanji with canonical KanjiVG stroke geometry.
7. Stamp SQLite `PRAGMA user_version` and generate the small version asset.
8. Run the existing dictionary verifier, including stroke/source and version consistency checks.

Dictionary v2 preserves source JMdict entries and unique exact writings in
`dictionary_word_schema.sql`. Valid reading combinations and sense applicability
retain `re_restr`, `stagk`, `stagr`, source order and common/priority metadata.
The verifier compares all imported applicability and metadata against the local XML.
No heuristic or transitive lexical merge is performed. Current release version: 3.

Outputs are `app/src/main/assets/dictionary.db` and
`app/src/main/assets/dictionary_version.txt`. Both are generated and Git-ignored,
as are source inputs under `tools/data/`; a fresh clone needs a full build before
packaging the APK. KanjiVG enriches existing kanji only; word identity/schema is unchanged.

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
python tools/download_kanjivg.py
python tools/import_kanjivg.py
python tools/verify_dictionary_with_override.py
```

Standalone importers do not stamp release metadata. Use the canonical full build
before distributing a dictionary; the verifier requires consistent release metadata.
The downloader skips existing sources and does not automatically update datasets.
Future import stages can be added to the ordered pipeline before stamping/verifying.

## KanjiVG release cache

First build with no selected cache discovers latest via the official endpoint
`https://api.github.com/repos/KanjiVG/kanjivg/releases/latest`, downloading its
`kanjivg-YYYYMMDD-main.zip`. Never use `all`, variant files or merged XML.
Cache is `tools/data/kanjivg/`, already Git-ignored. `selected.json` records release
tag, exact archive URL/name and SHA-256; do not commit the dataset/cache.

ZIP CRC, canonical filenames, 日 (`065e5.svg`) and the published asset digest
(when available) are validated. Temporary downloads/manifest updates publish only
after validation; failure preserves the prior selection. Ordinary builds reuse
verified selected cache without networking or silently updating datasets. Invalid
cache fails rather than choosing arbitrary older sources. A network failure on
an explicit latest check reuses only a verified selection with a visible warning.
No usable cache + no network fails before the KANJIDIC rebuild.

```sh
python tools/download_kanjivg.py --update
python tools/download_kanjivg.py --force
python tools/download_kanjivg.py --release r20260714
```

`--update` selects latest; `--force` redownloads the selected tag and can repair
an archive; combine both to redownload latest. `--release TAG` reproduces a known
official release on a fresh environment. Keep/copy manifest + archive or select
the same tag before full build. First builds selecting latest can differ across
dates; repeated builds with identical selected releases/other inputs do not.
Every build/import/verifier prints release; the manifest also exposes SHA-256.
The initial dictionary v3 build uses r20260714.

## Stroke enrichment

Canonical mapping is lowercase minimum-five-digit hex of the KANJIDIC Unicode
scalar (`日` -> `065e5.svg`; supplementary codepoints remain intact). Exact
filename, StrokePaths ID and consecutive `s1..sN` source IDs must agree.
Nested stroke paths retain geometry/direction/order. Number annotations and
component/type metadata are omitted. Missing files leave kanji valid without
strokes; malformed/unsupported found SVGs fail rather than silently skip.

Geometry uses the checked `viewBox="0 0 109 109"`, validated continuous path
syntax and no transforms. Unexpected viewBoxes/transforms need explicit support,
not lossy import. Component labels are not identity: r20260714 `06a45.svg` has a
stale root label 機 for 橅; the importer warns and uses filename/source IDs.

`kanji_stroke(kanji_id,stroke_index,path_data)` stores one path per ordered stroke,
not an SVG blob. Composite PK and FK enforce ownership; KANJIDIC rebuild drops
strokes before kanji. Enrichment runs after base imports, before stamping. Verifier
compares every stored path/index with selected source, checks integrity/FKs and
source/DB/sidecar versions. KANJIDIC stroke_count remains unchanged; differences
from KanjiVG are reported. Android consumes paths without parsing source XML.

KanjiVG: copyright Ulrich Apel, CC BY-SA 3.0. Upstream README/COPYING and the
notice in `app/src/main/assets/licenses/kanjivg.txt` describe the license.
Extracted stroke data remains CC BY-SA 3.0; annotations are omitted and unchanged
geometry is animated. About shows short attribution only. No runtime network added.

Python checks: `python -m unittest discover -s tools/tests -v`.
