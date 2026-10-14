"""Enrich existing KANJIDIC entities with canonical KanjiVG stroke geometry.

Extracted KanjiVG geometry: copyright Ulrich Apel, CC BY-SA 3.0.
See app/src/main/assets/licenses/kanjivg.txt. Geometry/direction is unmodified.
"""
import math
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET
import zipfile
from contextlib import closing
from dictionary_version import DB_PATH, TOOLS_DIR
from download_kanjivg import cached_source, archive_files

SVG = "{http://www.w3.org/2000/svg}"
NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|" + NUMBER)
ARITY = dict(M=2, L=2, H=1, V=1, C=6, S=4, Q=4, T=2, A=7, Z=0)


def validate_path(data):
    tokens = TOKEN.findall(data)
    if TOKEN.sub("", data).strip(" ,\t\r\n") or not tokens or tokens[0] not in ("M", "m"):
        raise ValueError(f"Invalid SVG path: {data[:60]}")
    command = None
    values = []
    moves = 0
    def finish():
        if command is None:
            return
        arity = ARITY[command.upper()]
        if (arity and (not values or len(values) % arity)) or (not arity and values):
            raise ValueError("Invalid SVG path argument count")
        if command.upper() == "A":
            for i in range(0, len(values), 7):
                if values[i] < 0 or values[i+1] < 0 or values[i+3] not in (0, 1) or values[i+4] not in (0, 1):
                    raise ValueError("Invalid SVG arc arguments")
    for token in tokens:
        if token in "MmLlHhVvCcSsQqTtAaZz":
            finish()
            command, values = token, []
            if token in "Mm":
                moves += 1
        else:
            value = float(token)
            if not math.isfinite(value):
                raise ValueError("Non-finite SVG coordinate")
            values.append(value)
    finish()
    if moves != 1:
        raise ValueError("A stroke must have exactly one continuous contour")
    return data.strip()


def parse_strokes(content, character):
    code = f"{ord(character):05x}"
    root = ET.fromstring(content)
    if root.tag != SVG + "svg" or [float(x) for x in root.get("viewBox", "").split()] != [0, 0, 109, 109]:
        raise ValueError(f"Unsupported KanjiVG viewBox for {character}")
    if root.get("transform"):
        raise ValueError(f"Unsupported root transform for {character}")
    groups = [g for g in root.findall(SVG + "g") if g.get("id") == f"kvg:StrokePaths_{code}"]
    if len(groups) != 1:
        raise ValueError(f"Missing/duplicate canonical StrokePaths for {character}")
    group = groups[0]
    identity = next((g for g in group.iter(SVG + "g") if g.get("id") == f"kvg:{code}"), None)
    if identity is None:
        raise ValueError(f"KanjiVG codepoint identity mismatch for {character}")
    element = next((v for k, v in identity.attrib.items() if k.endswith("}element")), None)
    if element != character:
        # Component labels are not identity. A released SVG can have a stale label
        # (r20260714 06a45.svg says 機 although the filename/IDs/geometry describe 橅).
        print(f"[ WARN ] KanjiVG {code}: root element label {element!r} differs from {character!r}; "
              "mapping uses canonical filename + source IDs, not component labels")
    paths = []
    for node in group.iter():
        if node.get("transform"):
            raise ValueError(f"Unsupported stroke transform for {character}; do not discard geometry")
        if node.tag == SVG + "path":
            if node.get("id") != f"kvg:{code}-s{len(paths)+1}":
                raise ValueError(f"Nonconsecutive stroke IDs for {character}")
            paths.append(validate_path(node.get("d", "")))
    if not paths:
        raise ValueError(f"Empty stroke data for {character}")
    return paths


def source_rows(db, archive_path):
    files = archive_files(archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        for kid, character in db.execute("SELECT id,character FROM kanji ORDER BY id"):
            if len(character) != 1:
                raise ValueError(f"Non-scalar KANJIDIC character: {character}")
            name = f"{ord(character):05x}.svg"
            if name in files:
                for index, path in enumerate(parse_strokes(archive.read(files[name]), character), 1):
                    yield kid, index, path


def enrich(db, archive_path):
    # Parse first: a malformed source never wipes previously imported strokes.
    rows = list(source_rows(db, archive_path))
    with db:
        if not db.in_transaction:
            db.execute("BEGIN")
        db.execute("DROP TABLE IF EXISTS kanji_stroke")
        db.execute((TOOLS_DIR / "dictionary_stroke_schema.sql").read_text(encoding="utf-8"))
        db.executemany("INSERT INTO kanji_stroke VALUES (?,?,?)", rows)
    return len({r[0] for r in rows}), len(rows)


def main():
    try:
        archive, manifest = cached_source()
        with closing(sqlite3.connect(DB_PATH.resolve().as_uri() + "?mode=rw", uri=True)) as db:
            db.execute("PRAGMA foreign_keys=ON")
            covered, strokes = enrich(db, archive)
            total = db.execute("SELECT COUNT(*) FROM kanji").fetchone()[0]
            disagreements = db.execute("""SELECT COUNT(*) FROM (SELECT k.id FROM kanji k JOIN kanji_stroke s ON s.kanji_id=k.id
                GROUP BY k.id HAVING COUNT(*) != k.stroke_count)""").fetchone()[0]
        print(f"KanjiVG {manifest['release']}: covered={covered}, uncovered={total-covered}, stroke rows={strokes}, "
              f"KANJIDIC stroke-count differences={disagreements}; KANJIDIC metadata unchanged")
        return 0
    except (OSError, ValueError, KeyError, sqlite3.Error, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"KANJIVG IMPORT FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
