"""Canonical full dictionary build; individual importers remain standalone."""
import os
import sqlite3
import subprocess
import sys
from contextlib import closing

from dictionary_version import (
    ASSET_VERSION_PATH, DB_PATH, TOOLS_DIR, read_version,
)


def stage(name, script):
    print(f"\n== {name} ==", flush=True)
    subprocess.run(
        [sys.executable, "-X", "utf8", str(TOOLS_DIR / script)],
        cwd=TOOLS_DIR.parent, check=True,
    )


def main():
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    version = None
    previous_size = DB_PATH.stat().st_size if DB_PATH.exists() else 0
    try:
        print("== Validate dictionary release version ==", flush=True)
        version = read_version()
        stage("Download required sources (reuse existing inputs)", "download_dictionary_sources.py")
        stage("Select verified KanjiVG release (reuse selected cache)", "download_kanjivg.py")
        stage("Import KANJIDIC2", "import_kanjidic.py")
        stage("Import JMdict", "import_jmdict.py")
        stage("Import JLPT and existing overrides", "import_jlpt_with_override.py")
        stage("Enrich existing kanji with canonical KanjiVG strokes", "import_kanjivg.py")
        print("\n== Stamp database and generate version asset ==", flush=True)
        with closing(sqlite3.connect(DB_PATH.resolve().as_uri() + "?mode=rw", uri=True)) as db:
            db.execute(f"PRAGMA user_version = {version}")
            db.commit()
        temporary = ASSET_VERSION_PATH.with_suffix(".txt.tmp")
        try:
            temporary.write_text(f"{version}\n", encoding="utf-8")
            os.replace(temporary, ASSET_VERSION_PATH)
        finally:
            temporary.unlink(missing_ok=True)
        stage("Verify dictionary including release metadata", "verify_dictionary_with_override.py")
    except (ValueError, OSError, sqlite3.Error, subprocess.CalledProcessError) as error:
        print(f"\nBUILD FAILED: {error}\nDatabase: {DB_PATH}\nDictionary version: {version if version is not None else 'invalid/unknown'}", file=sys.stderr)
        return 1
    size = DB_PATH.stat().st_size
    print(f"\nBUILD SUCCESS\nDatabase: {DB_PATH}\nDictionary version: {version}"
          f"\nDatabase bytes: {size} (before {previous_size}, delta {size-previous_size:+d})", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
